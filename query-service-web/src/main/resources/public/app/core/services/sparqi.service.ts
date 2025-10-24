import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, throwError, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import {
  SparqiMessage,
  SparqiContext,
  SparqiSession,
  SessionStorage,
  HealthResponse,
  SessionResponse,
  MessageResponse,
  SparqiMetricRecord,
  SparqiMetricsSummary
} from '../models/sparqi.models';

@Injectable({
  providedIn: 'root'
})
export class SparqiService {
  private readonly BASE_URL = '/queryrest/api/sparqi';
  private readonly STORAGE_PREFIX = 'sparqi-session-';
  private readonly USER_ID = '1'; // Placeholder user ID

  constructor(private http: HttpClient) {}

  /**
   * Check if SPARQi service is available
   */
  checkHealth(): Observable<HealthResponse> {
    return this.http.get<HealthResponse>(`${this.BASE_URL}/health`);
  }

  /**
   * Get or create a session for a route.
   * Checks localStorage first, creates new session if not found.
   */
  getOrCreateSession(routeId: string): Observable<SparqiSession> {
    // Check localStorage for existing session
    const stored = this.getStoredSession(routeId);

    if (stored) {
      // Session exists in localStorage, verify it's still valid
      return this.getSessionHistory(stored.sessionId).pipe(
        map(() => ({
          sessionId: stored.sessionId,
          userId: stored.userId,
          routeId: stored.routeId,
          createdAt: new Date(stored.createdAt)
        })),
        catchError(error => {
          // Session no longer valid, clear storage and create new
          console.warn('Stored session invalid, creating new session', error);
          this.clearStoredSession(routeId);
          return this.createSession(routeId);
        })
      );
    }

    // No stored session, create new one
    return this.createSession(routeId);
  }

  /**
   * Create a new SPARQi session
   */
  private createSession(routeId: string): Observable<SparqiSession> {
    return this.http.post<SessionResponse>(
      `${this.BASE_URL}/session?routeId=${encodeURIComponent(routeId)}&userId=${this.USER_ID}`,
      {}
    ).pipe(
      map(response => {
        const session: SparqiSession = {
          sessionId: response.sessionId,
          userId: response.userId,
          routeId: response.routeId,
          createdAt: new Date(response.createdAt),
          welcomeMessage: response.welcomeMessage
        };

        // Store in localStorage
        this.storeSession(routeId, session);

        return session;
      }),
      catchError(this.handleError)
    );
  }

  /**
   * Send a message to SPARQi
   */
  sendMessage(sessionId: string, message: string): Observable<SparqiMessage> {
    const headers = new HttpHeaders({ 'Content-Type': 'application/json' });

    return this.http.post<MessageResponse>(
      `${this.BASE_URL}/session/${sessionId}/message`,
      { message },
      { headers }
    ).pipe(
      map(response => ({
        role: response.role as 'user' | 'assistant' | 'system',
        content: response.content,
        timestamp: new Date(response.timestamp)
      })),
      catchError(this.handleError)
    );
  }

  /**
   * Get conversation history for a session
   */
  getSessionHistory(sessionId: string): Observable<SparqiMessage[]> {
    return this.http.get<SparqiMessage[]>(
      `${this.BASE_URL}/session/${sessionId}/history`
    ).pipe(
      map(messages => messages.map(msg => ({
        ...msg,
        timestamp: new Date(msg.timestamp)
      }))),
      catchError(this.handleError)
    );
  }

  /**
   * Get context information for a session
   */
  getSessionContext(sessionId: string): Observable<SparqiContext> {
    return this.http.get<SparqiContext>(
      `${this.BASE_URL}/session/${sessionId}/context`
    ).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Clear session for a route (delete from backend and localStorage)
   */
  clearSession(routeId: string): Observable<void> {
    const stored = this.getStoredSession(routeId);

    if (stored) {
      return this.http.delete<void>(
        `${this.BASE_URL}/session/${stored.sessionId}`
      ).pipe(
        tap(() => this.clearStoredSession(routeId)),
        catchError(error => {
          // Even if backend delete fails, clear localStorage
          this.clearStoredSession(routeId);
          return throwError(() => error);
        })
      );
    }

    return of(undefined);
  }

  /**
   * Terminate a specific session
   */
  terminateSession(sessionId: string): Observable<void> {
    return this.http.delete<void>(
      `${this.BASE_URL}/session/${sessionId}`
    ).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Get aggregated metrics summary for SPARQi usage
   */
  getMetricsSummary(): Observable<SparqiMetricsSummary> {
    return this.http.get<SparqiMetricsSummary>(
      `${this.BASE_URL}/metrics/summary`
    ).pipe(
      map(summary => ({
        ...summary,
        periodStart: new Date(summary.periodStart),
        periodEnd: new Date(summary.periodEnd)
      })),
      catchError(this.handleError)
    );
  }

  /**
   * Get historical metrics for visualization
   * @param hours Number of hours to look back (default: 24)
   */
  getMetricsHistory(hours: number = 168): Observable<SparqiMetricRecord[]> {
    return this.http.get<SparqiMetricRecord[]>(
      `${this.BASE_URL}/metrics/history?hours=${hours}`
    ).pipe(
      map(records => records.map(record => ({
        ...record,
        timestamp: new Date(record.timestamp)
      }))),
      catchError(this.handleError)
    );
  }

  /**
   * Get token counts grouped by route
   * Returns a map of route ID to total token count for pie chart visualization
   */
  getTokensByRoute(): Observable<Map<string, number>> {
    return this.http.get<{[key: string]: number}>(
      `${this.BASE_URL}/metrics/tokens-by-route`
    ).pipe(
      map(data => new Map(Object.entries(data).map(([k, v]) => [k, Number(v)]))),
      catchError(this.handleError)
    );
  }

  // ===== LocalStorage Management =====

  /**
   * Store session in localStorage
   */
  private storeSession(routeId: string, session: SparqiSession): void {
    const storage: SessionStorage = {
      sessionId: session.sessionId,
      userId: session.userId,
      routeId: session.routeId,
      createdAt: session.createdAt.toISOString()
    };

    localStorage.setItem(
      this.STORAGE_PREFIX + routeId,
      JSON.stringify(storage)
    );
  }

  /**
   * Get stored session from localStorage
   */
  private getStoredSession(routeId: string): SessionStorage | null {
    const key = this.STORAGE_PREFIX + routeId;
    const stored = localStorage.getItem(key);

    if (stored) {
      try {
        return JSON.parse(stored) as SessionStorage;
      } catch (error) {
        console.error('Failed to parse stored session', error);
        localStorage.removeItem(key);
      }
    }

    return null;
  }

  /**
   * Clear stored session from localStorage
   */
  private clearStoredSession(routeId: string): void {
    localStorage.removeItem(this.STORAGE_PREFIX + routeId);
  }

  /**
   * Handle HTTP errors
   */
  private handleError(error: any): Observable<never> {
    let errorMessage = 'An error occurred';

    if (error.error instanceof ErrorEvent) {
      // Client-side error
      errorMessage = error.error.message;
    } else if (error.error && error.error.error) {
      // Server-side error with error property
      errorMessage = error.error.error;
    } else if (error.status === 0) {
      errorMessage = 'Unable to connect to SPARQi service';
    } else if (error.status === 404) {
      errorMessage = 'Session not found or expired';
    } else if (error.status === 500) {
      errorMessage = 'SPARQi service error';
    }

    console.error('SPARQi Service Error:', error);
    return throwError(() => new Error(errorMessage));
  }
}
