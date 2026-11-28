import {
  AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, EventEmitter, forwardRef, Input, NgZone, OnChanges,
  OnDestroy, Output, SimpleChanges, ViewChild
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import * as monaco from 'monaco-editor';
import './monaco-environment';

/** Defaults under every editor's options (what the app configured for ngx-monaco-editor before). */
const DEFAULT_OPTIONS: monaco.editor.IStandaloneEditorConstructionOptions = {
  scrollBeyondLastLine: false,
  suggestOnTriggerCharacters: true,
  quickSuggestionsDelay: 0
};

/**
 * A Monaco editor as a form control (`formControlName`, `ngModel`). It uses the bundled ESM build of Monaco, the same
 * instance the rest of the app uses for languages, completion providers and markers.
 *
 *   <app-code-editor [options]="{ language: 'json' }" formControlName="body" (editorInit)="onInit($event)">
 *
 * The editor runs outside Angular's zone (typing doesn't trigger change detection); value changes are reported back
 * inside it.
 */
@Component({
  selector: 'app-code-editor',
  standalone: true,
  template: '<div class="editor-container" #container></div>',
  styles: [`
    :host { display: block; height: 200px; }
    .editor-container { width: 100%; height: 100%; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => CodeEditorComponent), multi: true }]
})
export class CodeEditorComponent implements AfterViewInit, OnChanges, OnDestroy, ControlValueAccessor {
  /** Editor construction options; later changes are applied with updateOptions (and setModelLanguage). */
  @Input() options: monaco.editor.IStandaloneEditorConstructionOptions = {};
  /** Emitted once, with the editor, after it has been created. */
  @Output() readonly editorInit = new EventEmitter<monaco.editor.IStandaloneCodeEditor>();

  @ViewChild('container', { static: true }) private container!: ElementRef<HTMLDivElement>;

  private editor?: monaco.editor.IStandaloneCodeEditor;
  private value = '';
  private disabled = false;
  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};

  constructor(private readonly zone: NgZone) {}

  ngAfterViewInit(): void {
    this.editor = this.zone.runOutsideAngular(() => monaco.editor.create(this.container.nativeElement, {
      ...DEFAULT_OPTIONS,
      ...this.options,
      value: this.value,
      readOnly: this.disabled || this.options.readOnly
    }));
    this.editor.onDidChangeModelContent(() => {
      const value = this.editor!.getValue();
      if (value === this.value) {
        return;
      }
      this.value = value;
      this.zone.run(() => this.onChange(value));
    });
    this.editor.onDidBlurEditorWidget(() => this.zone.run(() => this.onTouched()));
    this.editorInit.emit(this.editor);
  }

  ngOnChanges(changes: SimpleChanges): void {
    const options = changes['options'];
    if (!this.editor || !options || options.firstChange) {
      return;
    }
    const { language, value: _value, model: _model, ...rest } = this.options;
    this.editor.updateOptions({ ...rest, readOnly: this.disabled || rest.readOnly });
    const model = this.editor.getModel();
    if (language && model && model.getLanguageId() !== language) {
      monaco.editor.setModelLanguage(model, language);
    }
  }

  writeValue(value: string | null | undefined): void {
    this.value = value ?? '';
    if (this.editor && this.editor.getValue() !== this.value) {
      this.editor.setValue(this.value);
    }
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.disabled = disabled;
    this.editor?.updateOptions({ readOnly: disabled || this.options.readOnly });
  }

  ngOnDestroy(): void {
    this.editor?.dispose();
    this.editor = undefined;
  }
}
