import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogActions, MatDialogClose, MatDialogContent, MatDialogRef, MatDialogTitle } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { DatasourceService } from '../../core/services/datasource.service';
import { saveErrorMessage } from '../../core/http-error-message';

export interface DeleteDatasourceDialogData {
  dataSourceId: string;
  /** Routes using the datasource. */
  associatedRoutes?: string[];
}

/** Confirms deleting a datasource, naming the routes that use it; deletes, then returns to the list. */
@Component({
  selector: 'app-confirm-dialog',
  templateUrl: './delete-datasource-modal.component.html',
  styleUrls: ['./delete-datasource-modal.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogTitle, MatDialogContent, MatDialogActions, MatDialogClose, MatButton, MatIcon, MatProgressSpinner],
})
export class DeleteDatasourceModalComponent {
  protected readonly data = inject<DeleteDatasourceDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<DeleteDatasourceModalComponent, boolean>>(MatDialogRef);
  private readonly api = inject(DatasourceService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly deleting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected get routes(): string[] {
    return this.data.associatedRoutes ?? [];
  }

  protected deleteDatasource(): void {
    this.deleting.set(true);
    this.error.set(null);
    this.dialogRef.disableClose = true;
    this.api.deleteDatasource(this.data.dataSourceId).subscribe({
      next: () => {
        this.dialogRef.close(true);
        this.snackBar.open(`Deleted ${this.data.dataSourceId}`, 'Dismiss', { duration: 4000 });
        void this.router.navigate(['/datasources']);
      },
      error: error => {
        this.deleting.set(false);
        this.dialogRef.disableClose = false;
        this.error.set(saveErrorMessage(error));
      },
    });
  }
}
