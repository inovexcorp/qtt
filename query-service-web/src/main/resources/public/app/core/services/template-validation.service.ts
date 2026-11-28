import { Injectable, NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subject, of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import * as monaco from 'monaco-editor';

/** Result of POST /api/routes/template/validate. Positions are 1-based; end positions are inclusive. */
export interface TemplateValidation {
  valid: boolean;
  message?: string;
  line?: number;
  column?: number;
  endLine?: number;
  endColumn?: number;
}

/** The parts of a Monaco editor this service uses. */
export interface ValidatedEditor {
  getValue(): string;
  getModel(): any;
  onDidChangeModelContent(listener: () => void): { dispose(): void };
}

export type MarkerSetter = (model: any, markers: any[]) => void;

export const TEMPLATE_MARKER_OWNER = 'qtt-template-syntax';
const MARKER_SEVERITY_ERROR = 8; // monaco.MarkerSeverity.Error

const setMonacoMarkers: MarkerSetter = (model, markers) =>
  monaco.editor.setModelMarkers(model, TEMPLATE_MARKER_OWNER, markers);

/**
 * Checks FreeMarker template syntax with the server, which parses exactly as a route does, so the editor can mark
 * errors as the user types. Saving a template that does not parse is rejected by the server as well.
 */
@Injectable({
  providedIn: 'root'
})
export class TemplateValidationService {

  private validateUrl = '/queryrest/api/routes/template/validate';

  constructor(private http: HttpClient, private zone: NgZone) { }

  validate(template: string): Observable<TemplateValidation> {
    return this.http.post<TemplateValidation>(this.validateUrl, template ?? '',
      { headers: { 'Content-Type': 'text/plain' } });
  }

  /** Monaco marker for a syntax error, or none when the template is valid. */
  static markersFor(result: TemplateValidation): any[] {
    if (result.valid || !result.line) {
      return [];
    }
    const column = Math.max(result.column || 1, 1);
    const endLine = Math.max(result.endLine || result.line, result.line);
    const endColumn = endLine === result.line
      ? Math.max((result.endColumn || column) + 1, column + 1)
      : (result.endColumn || 1) + 1;
    return [{
      severity: MARKER_SEVERITY_ERROR,
      source: 'FreeMarker',
      message: result.message || 'Syntax error',
      startLineNumber: result.line,
      startColumn: column,
      endLineNumber: endLine,
      endColumn
    }];
  }

  /**
   * Validates the editor's content now and after each change (debounced), marking syntax errors in the editor.
   * Results for content that has since changed are dropped. If the server cannot be reached, markers are cleared
   * rather than left stale.
   *
   * @param onResult called with each result (null when validation failed), e.g. to show the error below the editor
   * @returns call dispose() when the editor goes away
   */
  attach(editor: ValidatedEditor,
         onResult?: (result: TemplateValidation | null) => void,
         debounceMs = 400,
         setMarkers: MarkerSetter = setMonacoMarkers): { dispose(): void } {
    const changes = new Subject<string>();
    const subscription = changes.pipe(
      debounceTime(debounceMs),
      distinctUntilChanged(),
      switchMap(text => this.validate(text).pipe(catchError(() => of(null))))
    ).subscribe(result => {
      const model = editor.getModel();
      if (model) {
        setMarkers(model, result ? TemplateValidationService.markersFor(result) : []);
      }
      // Editors run outside Angular's zone (app-code-editor); report results inside it so views update.
      this.zone.run(() => onResult?.(result));
    });
    const listener = editor.onDidChangeModelContent(() => changes.next(editor.getValue()));
    changes.next(editor.getValue());
    return {
      dispose: () => {
        listener.dispose();
        subscription.unsubscribe();
        const model = editor.getModel();
        if (model) {
          setMarkers(model, []);
        }
      }
    };
  }
}

// Kept here for existing imports; it lives in core/http-error-message.ts (no Monaco dependency).
export { saveErrorMessage } from '../http-error-message';
