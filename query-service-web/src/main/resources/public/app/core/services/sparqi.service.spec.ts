import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { SparqiService } from './sparqi.service';
import {
  SparqiSession,
  SparqiMessage,
  SparqiContext,
  HealthResponse,
  SessionResponse,
  MessageResponse,
  SparqiMetricsSummary,
  SparqiMetricRecord
} from '../models/sparqi.models';

describe('SparqiService', () => {
  let service: SparqiService;
  let httpMock: HttpTestingController;
  let localStorageSpy: jasmine.SpyObj<Storage>;

  beforeEach(() => {
    const storage: { [key: string]: string } = {};
    localStorageSpy = jasmine.createSpyObj('localStorage', ['getItem', 'setItem', 'removeItem']);
    localStorageSpy.getItem.and.callFake((key: string) => storage[key] || null);
    localStorageSpy.setItem.and.callFake((key: string, value: string) => {
      storage[key] = value;
    });
    localStorageSpy.removeItem.and.callFake((key: string) => {
      delete storage[key];
    });

    Object.defineProperty(window, 'localStorage', { value: localStorageSpy, writable: true });

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [SparqiService]
    });
    service = TestBed.inject(SparqiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('checkHealth', () => {
    it('should check if SPARQi service is available', () => {
      const mockHealthResponse: HealthResponse = {
        status: 'UP',
        message: 'SPARQi service is running'
      };

      service.checkHealth().subscribe(response => {
        expect(response).toEqual(mockHealthResponse);
        expect(response.status).toBe('UP');
      });

      const req = httpMock.expectOne('/queryrest/api/sparqi/health');
      expect(req.request.method).toBe('GET');
      req.flush(mockHealthResponse);
    });
  });

  describe('getOrCreateSession', () => {
    it('should create a new session when no stored session exists', () => {
      const routeId = 'test-route';
      const mockSessionResponse: SessionResponse = {
        sessionId: 'session-123',
        userId: '1',
        routeId: routeId,
        createdAt: new Date().toISOString(),
        welcomeMessage: 'Welcome to SPARQi!'
      };

      service.getOrCreateSession(routeId).subscribe(session => {
        expect(session.sessionId).toBe('session-123');
        expect(session.routeId).toBe(routeId);
        expect(session.welcomeMessage).toBe('Welcome to SPARQi!');
      });

      const req = httpMock.expectOne((request) =>
        request.url.includes('/queryrest/api/sparqi/session') &&
        request.url.includes(`routeId=${encodeURIComponent(routeId)}`)
      );
      expect(req.request.method).toBe('POST');
      req.flush(mockSessionResponse);
    });

    it('should return stored session if valid', () => {
      const routeId = 'test-route';
      const sessionId = 'stored-session-123';
      const storedSession = {
        sessionId: sessionId,
        userId: '1',
        routeId: routeId,
        createdAt: new Date().toISOString()
      };

      localStorage.setItem(`sparqi-session-${routeId}`, JSON.stringify(storedSession));

      const mockHistory: SparqiMessage[] = [
        { role: 'assistant', content: 'Hello', timestamp: new Date() }
      ];

      service.getOrCreateSession(routeId).subscribe(session => {
        expect(session.sessionId).toBe(sessionId);
      });

      const req = httpMock.expectOne(`/queryrest/api/sparqi/session/${sessionId}/history`);
      expect(req.request.method).toBe('GET');
      req.flush(mockHistory);
    });

    it('should create new session if stored session is invalid', () => {
      const routeId = 'test-route';
      const sessionId = 'invalid-session';
      const storedSession = {
        sessionId: sessionId,
        userId: '1',
        routeId: routeId,
        createdAt: new Date().toISOString()
      };

      localStorage.setItem(`sparqi-session-${routeId}`, JSON.stringify(storedSession));

      const mockNewSessionResponse: SessionResponse = {
        sessionId: 'new-session-456',
        userId: '1',
        routeId: routeId,
        createdAt: new Date().toISOString(),
        welcomeMessage: 'Welcome back!'
      };

      service.getOrCreateSession(routeId).subscribe(session => {
        expect(session.sessionId).toBe('new-session-456');
      });

      const historyReq = httpMock.expectOne(`/queryrest/api/sparqi/session/${sessionId}/history`);
      historyReq.error(new ProgressEvent('error'), { status: 404, statusText: 'Not Found' });

      const createReq = httpMock.expectOne((request) =>
        request.url.includes('/queryrest/api/sparqi/session') &&
        request.url.includes(`routeId=${encodeURIComponent(routeId)}`)
      );
      createReq.flush(mockNewSessionResponse);
    });
  });

  describe('sendMessage', () => {
    it('should send a message to SPARQi', () => {
      const sessionId = 'session-123';
      const message = 'What is SPARQL?';
      const mockResponse: MessageResponse = {
        role: 'assistant',
        content: 'SPARQL is a query language...',
        timestamp: new Date().toISOString()
      };

      service.sendMessage(sessionId, message).subscribe(response => {
        expect(response.role).toBe('assistant');
        expect(response.content).toContain('SPARQL');
      });

      const req = httpMock.expectOne(`/queryrest/api/sparqi/session/${sessionId}/message`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ message });
      expect(req.request.headers.get('Content-Type')).toBe('application/json');
      req.flush(mockResponse);
    });

    it('should handle message send errors', () => {
      const sessionId = 'session-123';
      const message = 'Test message';

      service.sendMessage(sessionId, message).subscribe(
        () => fail('should have failed'),
        error => {
          expect(error).toBeTruthy();
          expect(error.message).toContain('error');
        }
      );

      const req = httpMock.expectOne(`/queryrest/api/sparqi/session/${sessionId}/message`);
      req.error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });
    });
  });

  describe('getSessionHistory', () => {
    it('should retrieve conversation history', () => {
      const sessionId = 'session-123';
      const mockHistory = [
        { role: 'user' as const, content: 'Hello', timestamp: new Date().toISOString() },
        { role: 'assistant' as const, content: 'Hi!', timestamp: new Date().toISOString() }
      ];

      service.getSessionHistory(sessionId).subscribe(history => {
        expect(history.length).toBe(2);
        expect(history[0].role).toBe('user');
        expect(history[1].role).toBe('assistant');
      });

      const req = httpMock.expectOne(`/queryrest/api/sparqi/session/${sessionId}/history`);
      expect(req.request.method).toBe('GET');
      req.flush(mockHistory);
    });
  });

  describe('getSessionContext', () => {
    it('should retrieve session context', () => {
      const sessionId = 'session-123';
      const mockContext: SparqiContext = {
        routeId: 'test-route',
        routeName: 'Test Route',
        datasourceName: 'Test Datasource',
        ontologyElementCount: 100
      };

      service.getSessionContext(sessionId).subscribe(context => {
        expect(context.routeId).toBe('test-route');
        expect(context.ontologyElementCount).toBe(100);
      });

      const req = httpMock.expectOne(`/queryrest/api/sparqi/session/${sessionId}/context`);
      expect(req.request.method).toBe('GET');
      req.flush(mockContext);
    });
  });

  describe('clearSession', () => {
    it('should clear session from backend and localStorage', () => {
      const routeId = 'test-route';
      const sessionId = 'session-123';
      const storedSession = {
        sessionId: sessionId,
        userId: '1',
        routeId: routeId,
        createdAt: new Date().toISOString()
      };

      localStorage.setItem(`sparqi-session-${routeId}`, JSON.stringify(storedSession));

      service.clearSession(routeId).subscribe(() => {
        expect(localStorage.getItem(`sparqi-session-${routeId}`)).toBeNull();
      });

      const req = httpMock.expectOne(`/queryrest/api/sparqi/session/${sessionId}`);
      expect(req.request.method).toBe('DELETE');
      req.flush({});
    });

    it('should clear localStorage even if backend delete fails', () => {
      const routeId = 'test-route';
      const sessionId = 'session-123';
      const storedSession = {
        sessionId: sessionId,
        userId: '1',
        routeId: routeId,
        createdAt: new Date().toISOString()
      };

      localStorage.setItem(`sparqi-session-${routeId}`, JSON.stringify(storedSession));

      service.clearSession(routeId).subscribe(
        () => fail('should have failed'),
        () => {
          expect(localStorage.getItem(`sparqi-session-${routeId}`)).toBeNull();
        }
      );

      const req = httpMock.expectOne(`/queryrest/api/sparqi/session/${sessionId}`);
      req.error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });
    });

    it('should return undefined if no stored session exists', () => {
      const routeId = 'test-route';

      service.clearSession(routeId).subscribe(result => {
        expect(result).toBeUndefined();
      });

      httpMock.expectNone(`/queryrest/api/sparqi/session`);
    });
  });

  describe('terminateSession', () => {
    it('should terminate a specific session', () => {
      const sessionId = 'session-123';

      service.terminateSession(sessionId).subscribe(() => {
        expect(true).toBe(true);
      });

      const req = httpMock.expectOne(`/queryrest/api/sparqi/session/${sessionId}`);
      expect(req.request.method).toBe('DELETE');
      req.flush({});
    });
  });

  describe('getMetricsSummary', () => {
    it('should retrieve aggregated metrics summary', () => {
      const mockSummary: SparqiMetricsSummary = {
        totalSessions: 50,
        totalMessages: 200,
        totalTokens: 10000,
        averageTokensPerMessage: 50,
        periodStart: new Date().toISOString(),
        periodEnd: new Date().toISOString()
      };

      service.getMetricsSummary().subscribe(summary => {
        expect(summary.totalSessions).toBe(50);
        expect(summary.totalMessages).toBe(200);
        expect(summary.totalTokens).toBe(10000);
      });

      const req = httpMock.expectOne('/queryrest/api/sparqi/metrics/summary');
      expect(req.request.method).toBe('GET');
      req.flush(mockSummary);
    });
  });

  describe('getMetricsHistory', () => {
    it('should retrieve historical metrics with default hours', () => {
      const mockHistory: SparqiMetricRecord[] = [
        {
          timestamp: new Date().toISOString(),
          totalTokens: 100,
          messageCount: 5
        }
      ];

      service.getMetricsHistory().subscribe(history => {
        expect(history.length).toBe(1);
        expect(history[0].totalTokens).toBe(100);
      });

      const req = httpMock.expectOne('/queryrest/api/sparqi/metrics/history?hours=168');
      expect(req.request.method).toBe('GET');
      req.flush(mockHistory);
    });

    it('should retrieve historical metrics with custom hours', () => {
      const hours = 24;
      const mockHistory: SparqiMetricRecord[] = [];

      service.getMetricsHistory(hours).subscribe(history => {
        expect(history).toEqual([]);
      });

      const req = httpMock.expectOne(`/queryrest/api/sparqi/metrics/history?hours=${hours}`);
      expect(req.request.method).toBe('GET');
      req.flush(mockHistory);
    });
  });

  describe('getTokensByRoute', () => {
    it('should retrieve token counts grouped by route', () => {
      const mockData = {
        'route-1': 1000,
        'route-2': 2000,
        'route-3': 1500
      };

      service.getTokensByRoute().subscribe(tokenMap => {
        expect(tokenMap.size).toBe(3);
        expect(tokenMap.get('route-1')).toBe(1000);
        expect(tokenMap.get('route-2')).toBe(2000);
        expect(tokenMap.get('route-3')).toBe(1500);
      });

      const req = httpMock.expectOne('/queryrest/api/sparqi/metrics/tokens-by-route');
      expect(req.request.method).toBe('GET');
      req.flush(mockData);
    });

    it('should handle empty token data', () => {
      service.getTokensByRoute().subscribe(tokenMap => {
        expect(tokenMap.size).toBe(0);
      });

      const req = httpMock.expectOne('/queryrest/api/sparqi/metrics/tokens-by-route');
      req.flush({});
    });
  });

  describe('error handling', () => {
    it('should handle client-side errors', () => {
      const sessionId = 'session-123';

      service.getSessionContext(sessionId).subscribe(
        () => fail('should have failed'),
        error => {
          expect(error.message).toBeTruthy();
        }
      );

      const req = httpMock.expectOne(`/queryrest/api/sparqi/session/${sessionId}/context`);
      req.error(new ErrorEvent('Network error'));
    });

    it('should handle 404 errors with appropriate message', () => {
      const sessionId = 'session-123';

      service.getSessionHistory(sessionId).subscribe(
        () => fail('should have failed'),
        error => {
          expect(error.message).toContain('not found');
        }
      );

      const req = httpMock.expectOne(`/queryrest/api/sparqi/session/${sessionId}/history`);
      req.error(new ProgressEvent('error'), { status: 404, statusText: 'Not Found' });
    });

    it('should handle 500 errors with appropriate message', () => {
      service.checkHealth().subscribe(
        () => fail('should have failed'),
        error => {
          expect(error.message).toContain('service error');
        }
      );

      const req = httpMock.expectOne('/queryrest/api/sparqi/health');
      req.error(new ProgressEvent('error'), { status: 500, statusText: 'Internal Server Error' });
    });
  });
});
