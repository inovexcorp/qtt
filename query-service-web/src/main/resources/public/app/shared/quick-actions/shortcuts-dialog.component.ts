import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatDialogActions, MatDialogClose, MatDialogContent, MatDialogTitle } from '@angular/material/dialog';
import { MatButton } from '@angular/material/button';

/** True on Apple platforms, where the shortcut is Cmd (⌘) rather than Ctrl. */
export function isApple(platform: string = navigator.platform || navigator.userAgent): boolean {
  return /Mac|iPhone|iPad|iPod/i.test(platform);
}

interface Row {
  keys: string[];
  text: string;
}

/** The quick actions bar's keyboard shortcuts and search prefixes. */
@Component({
  selector: 'app-shortcuts-dialog',
  template: `
    <h2 mat-dialog-title>Keyboard shortcuts</h2>
    <mat-dialog-content>
      @for (section of sections; track section.title) {
        <h3>{{ section.title }}</h3>
        <dl>
          @for (row of section.rows; track row.text) {
            <div class="row">
              <dt>@for (key of row.keys; track $index) {<kbd>{{ key }}</kbd>}</dt>
              <dd>{{ row.text }}</dd>
            </div>
          }
        </dl>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Close</button>
    </mat-dialog-actions>
  `,
  styles: [`
    h3 { margin: 16px 0 6px; font: var(--mat-sys-title-small); color: var(--mat-sys-on-surface); }
    h3:first-child { margin-top: 0; }
    dl { margin: 0; }
    .row { display: flex; align-items: baseline; gap: 16px; padding: 5px 0; border-bottom: 1px solid var(--mat-sys-outline-variant); }
    .row:last-child { border-bottom: 0; }
    dt { flex: 0 0 112px; display: flex; gap: 4px; }
    dd { margin: 0; color: var(--mat-sys-on-surface-variant); }
    kbd {
      display: inline-block; min-width: 20px; padding: 1px 6px; box-sizing: border-box; border-radius: 4px;
      border: 1px solid var(--mat-sys-outline-variant); background: var(--mat-sys-surface-container);
      color: var(--mat-sys-on-surface); font: var(--mat-sys-label-medium); text-align: center;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogTitle, MatDialogContent, MatDialogActions, MatDialogClose, MatButton]
})
export class ShortcutsDialogComponent {
  private readonly mod = isApple() ? '⌘' : 'Ctrl';

  readonly sections: { title: string; rows: Row[] }[] = [
    {
      title: 'Anywhere',
      rows: [
        { keys: [this.mod, 'K'], text: 'Open the search bar, even while editing a template' },
        { keys: ['/'], text: 'Open the search bar (when not typing in a field)' },
        { keys: ['?'], text: 'Show these shortcuts (when not typing in a field)' }
      ]
    },
    {
      title: 'In the search bar',
      rows: [
        { keys: ['↑', '↓'], text: 'Move through the results' },
        { keys: ['↵'], text: 'Open or run the highlighted result' },
        { keys: ['Esc'], text: 'Close, and go back to where you were' },
        { keys: ['⌫'], text: 'In an empty search, stop narrowing it' }
      ]
    },
    {
      title: 'Start a search with',
      rows: [
        { keys: ['>'], text: 'Actions only' },
        { keys: ['#'], text: 'Routes only' },
        { keys: ['@'], text: 'Datasources only' },
        { keys: ['?'], text: 'These options' }
      ]
    }
  ];
}
