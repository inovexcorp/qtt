import { Component, Input, OnInit } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { SettingsService } from '../../core/services/settings.service';
import { effectiveAccess } from '../query-endpoint';

/**
 * The "Access" fields of a route form: who may call the route at /query/<routeId>, and the roles an authenticated
 * caller must also hold. Binds to the parent form's "accessPolicy" and "requiredRoles" controls.
 */
@Component({
    selector: 'app-route-access',
    template: `
    <div class="route-access" [formGroup]="group">
      <h3>Access</h3>
      <mat-form-field appearance="outline" class="access-select">
        <mat-label>Who can call this route</mat-label>
        <mat-select formControlName="accessPolicy" disableOptionCentering>
          <mat-option value="default">Server default ({{ serverDefault }})</mat-option>
          <mat-option value="open">Open: anyone who can reach the server</mat-option>
          <mat-option value="authenticated">Authenticated: a token with qtt-query, or an API key</mat-option>
        </mat-select>
        <mat-icon matSuffix>{{ effective === 'open' ? 'public' : 'lock' }}</mat-icon>
      </mat-form-field>
      <mat-form-field appearance="outline" class="roles-input" *ngIf="effective === 'authenticated'">
        <mat-label>Required roles (optional)</mat-label>
        <input matInput formControlName="requiredRoles" placeholder="e.g. finance-apps">
        <mat-hint>Comma-separated identity-provider roles or groups that callers must also hold. API keys must name this route.</mat-hint>
      </mat-form-field>
      <p class="access-note" *ngIf="effective === 'open'">
        <mat-icon>warning_amber</mat-icon> Anyone who can reach this server can run this query.
      </p>
    </div>`,
    styles: [`
    .route-access { margin: 8px 0 16px; }
    .access-select, .roles-input { width: 100%; max-width: 640px; display: block; }
    .access-note { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #8a6d00; margin: 0; }
    .access-note mat-icon { font-size: 18px; width: 18px; height: 18px; }
  `],
    standalone: false
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
