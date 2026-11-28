import { ChangeDetectionStrategy, Component, NgZone, ViewChild } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import * as monaco from 'monaco-editor';
import { CodeEditorComponent } from './code-editor.component';

@Component({
  standalone: true,
  imports: [CodeEditorComponent, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.Eager, // the test sets fields directly
  template: `<app-code-editor [formControl]="control" [options]="options" (editorInit)="editor = $event"
    style="height: 120px; width: 400px"></app-code-editor>`
})
class Host {
  control = new FormControl<string | null>('first');
  options: monaco.editor.IStandaloneEditorConstructionOptions = { language: 'json' };
  editor?: monaco.editor.IStandaloneCodeEditor;
  @ViewChild(CodeEditorComponent) component!: CodeEditorComponent;
}

describe('CodeEditorComponent', () => {
  let fixture: ComponentFixture<Host>;
  let host: Host;

  beforeEach(() => {
    fixture = TestBed.createComponent(Host);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => fixture.destroy());

  it('creates one Monaco editor with the options and emits it', () => {
    expect(host.editor).toBeDefined();
    expect(fixture.nativeElement.querySelectorAll('.monaco-editor').length).toBe(1);
    expect(host.editor!.getModel()!.getLanguageId()).toBe('json');
    expect(host.editor!.getOption(monaco.editor.EditorOption.scrollBeyondLastLine)).toBeFalse();
  });

  it('shows the form value, and follows later values', () => {
    expect(host.editor!.getValue()).toBe('first');
    host.control.setValue('{"a": 1}');
    expect(host.editor!.getValue()).toBe('{"a": 1}');
    host.control.setValue(null);
    expect(host.editor!.getValue()).toBe('');
  });

  it('reports typing back to the form control, inside the Angular zone', () => {
    let inZone: boolean | undefined;
    host.control.valueChanges.subscribe(() => inZone = NgZone.isInAngularZone());
    host.editor!.setValue('typed');
    expect(host.control.value).toBe('typed');
    expect(inZone).toBeTrue();
    expect(host.control.dirty).toBeTrue();
  });

  it('does not echo a value written by the form back as a change', () => {
    host.control.setValue('quiet', { emitEvent: false });
    host.control.markAsPristine();
    expect(host.editor!.getValue()).toBe('quiet');
    expect(host.control.dirty).toBeFalse();
  });

  it('is read-only while the control is disabled', () => {
    host.control.disable();
    expect(host.editor!.getOption(monaco.editor.EditorOption.readOnly)).toBeTrue();
    host.control.enable();
    expect(host.editor!.getOption(monaco.editor.EditorOption.readOnly)).toBeFalse();
  });

  it('applies changed options, including the language', () => {
    host.options = { language: 'freemarker2', wordWrap: 'on' };
    fixture.detectChanges();
    expect(host.editor!.getModel()!.getLanguageId()).toBe('freemarker2');
    expect(host.editor!.getOption(monaco.editor.EditorOption.wordWrap)).toBe('on');
  });

  it('disposes the editor with the component', () => {
    const model = host.editor!.getModel()!;
    fixture.destroy();
    expect(model.isDisposed()).toBeTrue();
  });
});

