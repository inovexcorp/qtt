import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialog, MatDialogActions, MatDialogClose, MatDialogContent, MatDialogTitle } from '@angular/material/dialog';
import { MatButton } from '@angular/material/button';
import { Observable, map } from 'rxjs';

export interface ConfirmDialogData {
  title: string;
  /** One paragraph per entry. */
  message: string[];
  confirmLabel: string;
}

/** A yes/no question before a change that is hard to undo. Closes with true when confirmed. */
@Component({
  selector: 'app-confirm-dialog',
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      @for (paragraph of data.message; track $index) {
        <p>{{ paragraph }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">Cancel</button>
      <button mat-flat-button class="confirm" [mat-dialog-close]="true">{{ data.confirmLabel }}</button>
    </mat-dialog-actions>
  `,
  styles: [`
    p { margin: 0 0 8px; }
    .confirm { --mat-button-filled-container-color: var(--mat-sys-error); --mat-button-filled-label-text-color: var(--mat-sys-on-error); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogTitle, MatDialogContent, MatDialogActions, MatDialogClose, MatButton]
})
export class ConfirmDialogComponent {
  readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
}

/** Opens the confirm dialog; emits true only when the user confirms. Cancel has focus, so Enter doesn't confirm. */
export function confirm(dialog: MatDialog, data: ConfirmDialogData): Observable<boolean> {
  return dialog.open(ConfirmDialogComponent, { data, width: '440px' })
    .afterClosed().pipe(map(result => result === true));
}
