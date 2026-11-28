import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatButton } from '@angular/material/button';

/**
 * Stands in for a metrics chart or table that has nothing to draw yet: a skeleton while its data first loads, a
 * message and Retry when the request failed, or an explanation when there is no data (rather than an empty or
 * zeroed chart). Fills its container.
 */
@Component({
  selector: 'app-metrics-status',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIcon, MatButton],
  host: { class: 'metrics-status-host' },
  template: `
    @switch (mode()) {
      @case ('loading') {
        <div class="metrics-skeleton" role="status" aria-live="polite">
          <span class="qtt-visually-hidden">{{ loadingText() }}</span>
          @for (width of skeletonRows; track $index) {
            <div class="metrics-skeleton__row">
              <span class="metrics-skeleton__label"></span>
              <span class="metrics-skeleton__bar" [style.width.%]="width"></span>
            </div>
          }
        </div>
      }
      @case ('error') {
        <div class="metrics-status" [attr.role]="retryable() ? 'alert' : 'status'">
          <mat-icon class="metrics-status__icon metrics-status__icon--error">error_outline</mat-icon>
          <p class="metrics-status__title">Couldn't load metrics</p>
          @if (retryable()) {
            <p class="metrics-status__detail">Check that the service is reachable, then try again.</p>
            <button mat-stroked-button (click)="retry.emit()">
              <mat-icon>refresh</mat-icon>
              Retry
            </button>
          } @else {
            <p class="metrics-status__detail">Retrying automatically.</p>
          }
        </div>
      }
      @default {
        <div class="metrics-status" role="status">
          <mat-icon class="metrics-status__icon">{{ emptyIcon() }}</mat-icon>
          <p class="metrics-status__title">{{ emptyTitle() }}</p>
          @if (emptyDetail()) {
            <p class="metrics-status__detail">{{ emptyDetail() }}</p>
          }
        </div>
      }
    }
  `,
})
export class MetricsStatusComponent {
  /** 'empty' when loaded but there is nothing to show. */
  readonly mode = input.required<'loading' | 'error' | 'empty'>();
  readonly loadingText = input('Loading metrics…');
  readonly emptyIcon = input('insights');
  readonly emptyTitle = input('No data yet');
  readonly emptyDetail = input('');
  /** Offer a Retry button on error (off where the page shows one Retry for everything). */
  readonly retryable = input(true);
  readonly retry = output<void>();

  protected readonly skeletonRows = [78, 52, 64, 36, 22];
}
