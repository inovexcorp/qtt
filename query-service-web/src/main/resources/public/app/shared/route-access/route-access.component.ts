import { Component, Input, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { SettingsService } from '../../core/services/settings.service';
import { effectiveAccess } from '../query-endpoint';
import { MatFormField, MatLabel, MatSuffix, MatHint } from '@angular/material/form-field';
import { MatSelect, MatOption } from '@angular/material/select';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';

/**
 * The "Access" fields of a route form: who may call the route at /query/<routeId>, and the roles an authenticated
 * caller must also hold. Binds to the parent form's "accessPolicy" and "requiredRoles" controls.
 */
@Component({
    selector: 'app-route-access',
    template: `
    <div class="route-access" [formGroup]="group">
      <mat-form-field appearance="outline" subscriptSizing="dynamic" class="access-select">
        <mat-label>Who can call this route</mat-label>
        <mat-select formControlName="accessPolicy" disableOptionCentering>
          <mat-option value="default">Server default ({{ serverDefault }})</mat-option>
          <mat-option value="open">Open: anyone who can reach the server</mat-option>
          <mat-option value="authenticated">Authenticated: a token with qtt-query, or an API key</mat-option>
        </mat-select>
        <mat-icon matSuffix>{{ effective === 'open' ? 'public' : 'lock' }}</mat-icon>
      </mat-form-field>
      @if (effective === 'authenticated') {
        <mat-form-field appearance="outline" subscriptSizing="dynamic" class="roles-input">
          <mat-label>Required roles (optional)</mat-label>
          <input matInput formControlName="requiredRoles" placeholder="e.g. finance-apps">
          <mat-hint>Comma-separated identity-provider roles or groups that callers must also hold. API keys must name this route.</mat-hint>
        </mat-form-field>
      }
      @if (effective === 'open') {
        <p class="access-note">
          <mat-icon aria-hidden="true">warning_amber</mat-icon>
          <span>Anyone who can reach this server can run this query.</span>
        </p>
      }
    </div>`,
    styles: [`
    .route-access { display: flex; flex-direction: column; gap: 1rem; }
    .access-select, .roles-input { width: 100%; display: block; }
    .access-note { display: flex; align-items: flex-start; gap: 0.5rem; margin: 0; padding: 0.625rem 0.75rem;
      border-radius: 8px; font-size: 0.875rem; color: var(--mat-sys-on-surface);
      background: color-mix(in srgb, var(--qtt-warn) 10%, var(--mat-sys-surface-container-lowest)); }
    .access-note mat-icon { flex: none; font-size: 20px; width: 20px; height: 20px; color: var(--qtt-warn); }
  `],
    changeDetection: ChangeDetectionStrategy.Eager,
    imports: [FormsModule, ReactiveFormsModule, MatFormField, MatLabel, MatSelect, MatOption, MatIcon, MatSuffix, MatInput, MatHint]
})
export class RouteAccessComponent implements OnInit {
  @Input() group!: FormGroup;
  /** QUERY_AUTH_DEFAULT on the server; "open" until it is known. */
  serverDefault: 'open' | 'authenticated' = 'open';

  constructor(private settings: SettingsService) {}

  ngOnInit(): void {
    this.settings.getSsoInfo().subscribe({
      next: info => this.serverDefault = info.queryAuthDefault === 'authenticated' ? 'authenticated' : 'open',
      error: () => { /* keep "open": the label is informational, the server decides */ }
    });
  }

  get effective(): 'open' | 'authenticated' {
    return effectiveAccess(this.group.get('accessPolicy')?.value, this.serverDefault);
  }
}
