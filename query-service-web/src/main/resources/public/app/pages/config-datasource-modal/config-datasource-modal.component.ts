import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogActions, MatDialogClose, MatDialogContent, MatDialogRef, MatDialogTitle } from '@angular/material/dialog';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { DatasourceService } from '../../core/services/datasource.service';
import { Datasources } from '../../core/models/datasources';
import { saveErrorMessage } from '../../core/http-error-message';

/** One changed setting; from/to are null for a secret (the password). */
export interface DatasourceChange {
  label: string;
  from: string | null;
  to: string | null;
}

export interface SaveDatasourceDialogData {
  datasource: Datasources;
  changes: DatasourceChange[];
  associatedRoutes: string[];
}

/**
 * Confirms saving a datasource's settings: what changes, and which routes use it. Saves itself (so progress and
 * a failure show here) and closes with true once saved.
 */
@Component({
  selector: 'app-config-datasource-modal',
  templateUrl: './config-datasource-modal.component.html',
  styleUrls: ['./config-datasource-modal.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogTitle, MatDialogContent, MatDialogActions, MatDialogClose, MatButton, MatIcon, MatProgressSpinner],
})
export class ConfigDatasourceModalComponent {
  protected readonly data = inject<SaveDatasourceDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<ConfigDatasourceModalComponent, boolean>>(MatDialogRef);
  private readonly api = inject(DatasourceService);

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected get routes(): string[] {
    return this.data.associatedRoutes ?? [];
  }

  protected save(): void {
    this.saving.set(true);
    this.error.set(null);
    this.dialogRef.disableClose = true;
    this.api.modifyDatasource(this.data.datasource).subscribe({
      next: () => this.dialogRef.close(true),
      error: error => {
        this.saving.set(false);
        this.dialogRef.disableClose = false;
        this.error.set(saveErrorMessage(error));
      },
    });
  }
}
