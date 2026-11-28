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
      <h3>Endpoint</h3>
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
    .endpoint-panel { margin: 8px 0 16px; }
    .endpoint-row { display: flex; align-items: center; gap: 8px; }
    .endpoint-row code { flex: 1; overflow-x: auto; white-space: nowrap; padding: 6px 8px; background: rgba(0,0,0,0.05); border-radius: 4px; font-size: 13px; }
    .endpoint-hint { font-size: 12px; color: rgba(0,0,0,0.6); margin: 4px 0 0 32px; }
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
