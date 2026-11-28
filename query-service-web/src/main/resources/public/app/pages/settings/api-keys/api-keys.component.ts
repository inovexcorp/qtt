import { Component, ElementRef, OnInit, ViewChild, ChangeDetectionStrategy } from '@angular/core';
import { FormControl, FormGroup, FormGroupDirective, Validators, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Clipboard } from '@angular/cdk/clipboard';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiKey, ApiKeysService, CreatedApiKey } from '../../../core/services/api-keys.service';
import { saveErrorMessage } from '../../../core/http-error-message';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { MatFormField, MatLabel, MatHint } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { DatePipe } from '@angular/common';

/**
 * API keys for the query routes (admin only): list, create (the key is shown once, with a copy button) and revoke.
 */
@Component({
    selector: 'app-api-keys',
    templateUrl: './api-keys.component.html',
    styleUrls: ['./api-keys.component.scss'],
    changeDetection: ChangeDetectionStrategy.Eager,
    imports: [MatSlideToggle, FormsModule, MatButton, MatIcon, MatIconButton, MatTooltip, ReactiveFormsModule, MatFormField, MatLabel, MatInput, MatHint, DatePipe]
})
export class ApiKeysComponent implements OnInit {

  keys: ApiKey[] = [];
  loadFailed = false;
  showRevoked = false;
  /** The key just created; its secret is shown until the admin dismisses it, and never again. */
  created: CreatedApiKey | null = null;
  creating = false;
  /** The create form stays folded away behind "New API key" until it is wanted. */
  showForm = false;

  form = new FormGroup({
    name: new FormControl('', [Validators.required, Validators.maxLength(100)]),
    description: new FormControl('', Validators.maxLength(500)),
    routes: new FormControl('', Validators.required),
    expiresInDays: new FormControl<number | null>(null, Validators.min(1))
  });

  /** Reset through the directive too, so the cleared required fields aren't shown as errors after a submit. */
  @ViewChild(FormGroupDirective) private formDirective?: FormGroupDirective;
  @ViewChild('nameInput') private nameInput?: ElementRef<HTMLInputElement>;

  constructor(private apiKeys: ApiKeysService, private clipboard: Clipboard, private snackBar: MatSnackBar) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.apiKeys.list().subscribe({
      next: keys => { this.keys = keys; this.loadFailed = false; },
      error: () => this.loadFailed = true
    });
  }

  get visibleKeys(): ApiKey[] {
    return this.showRevoked ? this.keys : this.keys.filter(k => k.status !== 'revoked');
  }

  /** The "Show revoked" toggle only appears when there is something to show. */
  get hasRevoked(): boolean {
    return this.keys.some(k => k.status === 'revoked');
  }

  openForm(): void {
    this.showForm = true;
    setTimeout(() => this.nameInput?.nativeElement.focus());
  }

  /** Hides the create form and clears what was typed. */
  closeForm(): void {
    this.showForm = false;
    this.form.reset();
  }

  /** "people, places" or "*" → ["people", "places"]. */
  static parseRoutes(text: string | null | undefined): string[] {
    return (text || '').split(/[,\s]+/).map(r => r.trim()).filter(r => r.length > 0);
  }

  create(): void {
    if (this.form.invalid) {
      return;
    }
    const value = this.form.value;
    this.creating = true;
    this.apiKeys.create({
      name: (value.name || '').trim(),
      description: (value.description || '').trim() || undefined,
      routes: ApiKeysComponent.parseRoutes(value.routes),
      expiresInDays: value.expiresInDays || undefined
    }).subscribe({
      next: key => {
        this.created = key;
        this.creating = false;
        this.showForm = false;
        if (this.formDirective) {
          this.formDirective.resetForm();
        } else {
          this.form.reset();
        }
        this.load();
      },
      error: error => {
        this.creating = false;
        this.snackBar.open(`API key not created: ${saveErrorMessage(error)}`, 'Close',
          { duration: 10000, panelClass: ['error-snackbar'] });
      }
    });
  }

  copyCreated(): void {
    if (this.created) {
      const copied = this.clipboard.copy(this.created.key);
      this.snackBar.open(copied ? 'Copied the API key' : 'Could not copy the API key', 'Close', { duration: 3000 });
    }
  }

  dismissCreated(): void {
    this.created = null;
  }

  revoke(key: ApiKey): void {
    if (!confirm(`Revoke the API key "${key.name}" (${key.id})? Callers using it are refused within 30 seconds.`)) {
      return;
    }
    this.apiKeys.revoke(key.id).subscribe({
      next: () => {
        this.snackBar.open(`Revoked "${key.name}"`, 'Close', { duration: 3000 });
        this.load();
      },
      error: error => this.snackBar.open(`Not revoked: ${saveErrorMessage(error)}`, 'Close',
        { duration: 10000, panelClass: ['error-snackbar'] })
    });
  }

  formatRoutes(routes: string[]): string {
    return routes.map(r => r === '*' ? 'all authenticated routes without required roles' : r).join(', ');
  }
}
