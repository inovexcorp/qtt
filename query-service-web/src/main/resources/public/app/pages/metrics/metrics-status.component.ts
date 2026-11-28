import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatIcon } from '@angular/material/icon';
import { MatButton } from '@angular/material/button';

/**
 * Stands in for a metrics chart or table that has nothing to draw yet: a spinner while its data loads, a message and
 * Retry when the request failed, or an explanation when there is no data (rather than an empty or zeroed chart).
 */
@Component({
  selector: 'app-metrics-status',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatProgressSpinner, MatIcon, MatButton],
  template: `
    <div class="metrics-status" [attr.role]="mode() === 'error' ? 'alert' : 'status'" aria-live="polite">
      @switch (mode()) {
        @case ('loading') {
          <mat-spinner diameter="40" aria-label="Loading"></mat-spinner>
          <p class="metrics-status__title">{{ loadingText() }}</p>
        }
        @case ('error') {
          <mat-icon class="metrics-status__icon metrics-status__icon--error">error_outline</mat-icon>
          <p class="metrics-status__title">Couldn't load metrics</p>
          <p class="metrics-status__detail">Check that the service is reachable, then try again.</p>
          <button mat-stroked-button (click)="retry.emit()">
            <mat-icon>refresh</mat-icon>
            Retry
          </button>
        }
        @default {
          <mat-icon class="metrics-status__icon">{{ emptyIcon() }}</mat-icon>
          <p class="metrics-status__title">{{ emptyTitle() }}</p>
          @if (emptyDetail()) {
            <p class="metrics-status__detail">{{ emptyDetail() }}</p>
          }
        }
      }
    </div>
  `,
})
export class MetricsStatusComponent {
  /** 'empty' when loaded but there is nothing to show. */
  readonly mode = input.required<'loading' | 'error' | 'empty'>();
  readonly loadingText = input('Loading metrics…');
  readonly emptyIcon = input('insights');
  readonly emptyTitle = input('No data yet');
  readonly emptyDetail = input('');
  readonly retry = output<void>();
}
