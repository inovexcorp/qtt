import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, inject, input, output, signal, viewChild } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import * as monaco from 'monaco-editor';
import { CodeEditorComponent } from '../../../shared/code-editor/code-editor.component';
import { TemplateValidation, TemplateValidationService } from '../../../core/services/template-validation.service';
import { OntologyService } from '../../../core/services/ontology/ontology.service';
import { OntologyAutocompleteProvider, registerOntologyAutocomplete } from '../../../core/services/ontology/ontology-autocomplete.provider';
import { RouteDraft } from '../route-draft';

/** The editor's options: FreeMarker, with completion from the route's ontology. */
export const TEMPLATE_EDITOR_OPTIONS: monaco.editor.IStandaloneEditorConstructionOptions = {
  theme: 'vs-dark',
  language: 'freemarker2',
  automaticLayout: true,
  minimap: { enabled: false },
  fontSize: 13,
  lineHeight: 20,
  padding: { top: 12, bottom: 12 },
  scrollBeyondLastLine: false,
  suggest: { showWords: true, showSnippets: true, snippetsPreventQuickSuggestions: false },
  quickSuggestions: { other: true, comments: false, strings: true },
  acceptSuggestionOnCommitCharacter: true,
  acceptSuggestionOnEnter: 'on',
  tabCompletion: 'on',
};

/**
 * A route's FreeMarker template: the editor (syntax checked as you type, ontology completion), uploading a file in
 * its place, and full screen. The editor is the draft's `template` control.
 */
@Component({
  selector: 'qtt-route-template-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class.is-fullscreen]': 'fullscreen()' },
  imports: [ReactiveFormsModule, CodeEditorComponent, MatButton, MatIconButton, MatIcon, MatTooltip],
  template: `
    <div class="te" [formGroup]="draft.form">
      <div class="te__bar">
        <span class="te__lang">FreeMarker → SPARQL</span>
        @if (error(); as e) {
          <span class="te__error" role="status">
            <mat-icon aria-hidden="true">error_outline</mat-icon>
            @if (e.line) { Line {{ e.line }}{{ e.column ? ', column ' + e.column : '' }}: }{{ e.message || 'The template does not parse' }}
          </span>
        } @else if (draft.form.controls.template.value.trim()) {
          <span class="te__ok"><mat-icon aria-hidden="true">check</mat-icon>Parses</span>
        } @else {
          <span class="te__hint">Empty: the route is created stopped until it has a template</span>
        }
        <span class="te__spacer"></span>
        @if (!readonly()) {
          <input #file type="file" accept=".ftl,.txt,.sparql,.rq,text/plain" hidden (change)="upload($event)">
          <button mat-button type="button" (click)="file.click()" matTooltip="Replace the template with a file's contents">
            <mat-icon>upload_file</mat-icon>
            Upload
          </button>
        }
        <ng-content select="[teActions]"></ng-content>
        <button mat-icon-button type="button" (click)="toggleFullscreen()"
          [matTooltip]="fullscreen() ? 'Exit full screen (Esc)' : 'Full screen'"
          [attr.aria-label]="fullscreen() ? 'Exit full screen' : 'Full screen'">
          <mat-icon>{{ fullscreen() ? 'fullscreen_exit' : 'fullscreen' }}</mat-icon>
        </button>
      </div>
      <app-code-editor class="te__editor" formControlName="template" [options]="options"
        (editorInit)="onEditorInit($event)" (keydown.escape)="fullscreen() && toggleFullscreen()"></app-code-editor>
      @if (fileName()) {
        <p class="te__file">Loaded from {{ fileName() }}. Save to keep it.</p>
      }
    </div>
  `,
  styleUrl: './route-form.scss',
})
export class RouteTemplateEditorComponent {
  protected readonly draft = inject(RouteDraft);
  private readonly validation = inject(TemplateValidationService);
  private readonly ontology = inject(OntologyService);

  /** The route whose ontology completes terms ('' for a route not created yet). */
  readonly routeId = input('');
  readonly readonly = input(false);
  readonly fullscreenChange = output<boolean>();

  protected readonly error = signal<TemplateValidation | null>(null);
  protected readonly fileName = signal('');
  protected readonly fullscreen = signal(false);
  protected options = TEMPLATE_EDITOR_OPTIONS;

  private editor?: monaco.editor.IStandaloneCodeEditor;
  private completion?: OntologyAutocompleteProvider;
  private attached?: { dispose(): void };
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.attached?.dispose();
      this.completion?.dispose(); // registered globally with Monaco: it outlived the page before
    });
  }

  /** The Monaco editor, once created (for relayout when its box changes). */
  get monacoEditor(): monaco.editor.IStandaloneCodeEditor | undefined {
    return this.editor;
  }

  protected onEditorInit(editor: monaco.editor.IStandaloneCodeEditor): void {
    this.editor = editor;
    editor.updateOptions({ readOnly: this.readonly() });
    this.attached = this.validation.attach(editor, result => this.error.set(result && !result.valid ? result : null));
    // URIs, prefixed names and FreeMarker expressions are words to complete, not broken at dots and slashes
    monaco.languages.setLanguageConfiguration('freemarker2', {
      wordPattern: /(-?\d*\.\d\w*)|([^`~!@#%^&*()=+[{\]}\\|;:'",?\s]+)/g,
    });
    const id = this.routeId();
    this.completion = registerOntologyAutocomplete(this.ontology, id || 'new-route-placeholder');
    if (id) {
      this.ontology.warmCache(id).subscribe({ error: () => { /* completion just has less to offer */ } });
    }
  }

  protected upload(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      this.draft.form.controls.template.setValue(String(reader.result ?? ''));
      this.draft.form.controls.template.markAsDirty();
      this.fileName.set(file.name);
    };
    reader.readAsText(file);
    input.value = '';
  }

  toggleFullscreen(): void {
    this.fullscreen.update(f => !f);
    this.fullscreenChange.emit(this.fullscreen());
    // The editor resizes with its box (automaticLayout); focus it so typing continues
    setTimeout(() => {
      this.editor?.layout();
      this.editor?.focus();
      this.host.nativeElement.scrollIntoView({ block: 'nearest' });
    }, 0);
  }
}
