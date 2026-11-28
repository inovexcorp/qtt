import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { Clipboard } from '@angular/cdk/clipboard';
import { MatSnackBar } from '@angular/material/snack-bar';
import { curlExample, endpointUrl } from '../query-endpoint';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIconButton } from '@angular/material/button';

/** A route's endpoint URL and a curl command to call it, each with a copy button. */
@Component({
    selector: 'app-endpoint-panel',
    template: `
    <div class="endpoint-panel">
      <div class="endpoint-row">
        <mat-icon [matTooltip]="access === 'open' ? 'Open: no credentials needed' : 'Needs a token with qtt-query or an API key'">
        {{ access === 'open' ? 'public' : 'lock' }}</mat-icon>
        <code class="endpoint-url">{{ url }}</code>
        <button mat-icon-button type="button" (click)="copy(url, 'URL')" matTooltip="Copy URL"><mat-icon>content_copy</mat-icon></button>
      </div>
      <div class="endpoint-row">
        <mat-icon>terminal</mat-icon>
        <code class="endpoint-curl">{{ curl }}</code>
        <button mat-icon-button type="button" (click)="copy(curl, 'curl command')" matTooltip="Copy curl command"><mat-icon>content_copy</mat-icon></button>
      </div>
      @if (access === 'authenticated') {
        <p class="endpoint-hint">
          Set <code>QTT_TOKEN</code> to an OIDC access token that carries <code>qtt-query</code>, or to a QTT API key (Settings › Query Access).
        </p>
      }
    </div>`,
    styles: [`
    .endpoint-panel { display: flex; flex-direction: column; gap: 0.25rem; padding: 0.375rem 0.25rem 0.375rem 0.75rem;
      border-radius: 10px; background: var(--mat-sys-surface-container-low); }
    .endpoint-row { display: flex; align-items: center; gap: 0.5rem; min-width: 0; }
    .endpoint-row > mat-icon { flex: none; width: 18px; height: 18px; font-size: 18px; color: var(--qtt-muted); }
    .endpoint-row code { flex: 1; min-width: 0; overflow-x: auto; white-space: nowrap; scrollbar-width: thin;
      font: 0.8125rem/1.5rem ui-monospace, 'SF Mono', Menlo, Consolas, monospace; color: var(--mat-sys-on-surface); }
    .endpoint-row .mat-mdc-icon-button { --mat-icon-button-state-layer-size: 36px; --mat-icon-button-icon-size: 18px;
      --mat-icon-button-icon-color: var(--qtt-muted); padding: 9px; flex: none; }
    .endpoint-hint { font-size: 0.75rem; color: var(--qtt-muted); margin: 0 0.5rem 0.25rem 1.625rem; }
    .endpoint-hint code { font-size: 0.75rem; }
  `],
    changeDetection: ChangeDetectionStrategy.Eager,
    imports: [MatIcon, MatTooltip, MatIconButton]
})
export class EndpointPanelComponent {
  @Input() baseUrl: string | null | undefined;
  @Input() routeId = '';
  @Input() access: 'open' | 'authenticated' = 'open';
  @Input() methods: string[] = ['GET'];

  constructor(private clipboard: Clipboard, private snackBar: MatSnackBar) {}

  get url(): string {
    return endpointUrl(this.baseUrl, this.routeId);
  }

  get curl(): string {
    return curlExample(this.url, this.access, this.methods);
  }

  copy(text: string, what: string): void {
    const copied = this.clipboard.copy(text);
    this.snackBar.open(copied ? `Copied the ${what}` : `Could not copy the ${what}`, 'Close', { duration: 3000 });
  }
}
