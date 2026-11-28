import { afterNextRender, ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { MatTooltip } from '@angular/material/tooltip';
import { HealthCheck } from '../../core/models/datasource-health';
import { checksSummary, relativeTime } from './health-status';

/**
 * A datasource's recent health checks as a row of bars, oldest on the left: a bar's height is how long the check
 * took (relative to the slowest shown), a failed check is a full-height bar in the error colour, and slots not yet
 * filled are faint. When a check lands, its bar grows in at the right while the oldest folds away on the left, so the
 * row reads as time moving.
 *
 * compact: in a list card. full: on the datasource page, taller, with times under the ends and the slowest time.
 */
@Component({
  selector: 'qtt-check-history',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatTooltip],
  host: { '[class.is-ready]': 'ready()', '[class.ch--full]': "size() === 'full'", '[class.ch--dim]': 'dimmed()' },
  template: `
    <div class="ch__bars" role="img" [attr.aria-label]="summary()">
      @for (slot of placeholders(); track slot) {
        <span class="ch__slot ch__slot--empty"><span class="ch__bar"></span></span>
      }
      @for (check of checks(); track check.checkTime) {
        <span class="ch__slot" animate.enter="ch-slot-in" animate.leave="ch-slot-out"
          [matTooltip]="tooltip(check)" matTooltipPosition="above">
          <span class="ch__bar" [attr.data-status]="check.status" [style.transform]="'scaleY(' + fraction(check) + ')'"></span>
        </span>
      }
    </div>
    @if (size() === 'full') {
      <div class="ch__axis" aria-hidden="true">
        <span>{{ oldestLabel() }}</span>
        @if (slowest() !== null) {
          <span class="ch__axis-max">Slowest {{ slowest() }} ms</span>
        }
        <span>{{ latestLabel() }}</span>
      </div>
    }
  `,
  styles: `
    :host { display: block; min-width: 0; --ch-height: 1.75rem; }
    :host(.ch--full) { --ch-height: 4.5rem; max-width: 30rem; margin-left: auto; }
    .ch__bars {
      display: flex;
      align-items: flex-end;
      height: var(--ch-height);
    }
    .ch__slot {
      display: flex;
      align-items: flex-end;
      flex: 1 1 0;
      min-width: 0;
      height: 100%;
      padding: 0 1.5px;
    }
    :host(.ch--full) .ch__slot { padding: 0 4px; }
    .ch__bar {
      display: block;
      width: 100%;
      height: 100%;
      border-radius: 2px 2px 1px 1px;
      background: color-mix(in srgb, var(--mat-sys-primary) 55%, var(--mat-sys-surface-container-lowest));
      transform-origin: bottom center;
      transition: transform 500ms cubic-bezier(0.16, 1, 0.3, 1), background-color 250ms ease;
    }
    .ch__bar[data-status='DOWN'] { background: var(--mat-sys-error); }
    .ch__slot--empty .ch__bar {
      height: 3px;
      background: var(--mat-sys-surface-container-highest);
    }
    :host(.ch--dim) .ch__bars { opacity: 0.45; filter: grayscale(1); }
    .ch__slot:hover .ch__bar:not([data-status='DOWN']) { background: var(--mat-sys-primary); }

    // A check lands: its slot opens at the right as the oldest closes at the left, and its bar grows up.
    :host(.is-ready) .ch-slot-in { animation: ch-slot-in 600ms cubic-bezier(0.16, 1, 0.3, 1); }
    :host(.is-ready) .ch-slot-in .ch__bar { animation: ch-bar-grow 480ms 120ms cubic-bezier(0.16, 1, 0.3, 1) backwards; }
    .ch-slot-out { animation: ch-slot-out 450ms cubic-bezier(0.16, 1, 0.3, 1) forwards; }
    @keyframes ch-slot-in { from { flex-grow: 0; padding: 0; } }
    @keyframes ch-bar-grow { from { transform: scaleY(0); } }
    @keyframes ch-slot-out { to { flex-grow: 0; padding: 0; opacity: 0; } }

    .ch__axis {
      display: flex;
      justify-content: space-between;
      gap: 1rem;
      margin-top: 0.5rem;
      font-size: 0.75rem;
      color: var(--qtt-muted);
      font-variant-numeric: tabular-nums;
    }
    .ch__axis-max { color: var(--mat-sys-outline); }

    @media (prefers-reduced-motion: reduce) {
      :host(.is-ready) .ch-slot-in, :host(.is-ready) .ch-slot-in .ch__bar, .ch-slot-out { animation-duration: 1ms; animation-delay: 0ms; }
      .ch__bar { transition: background-color 250ms ease; }
    }
  `,
})
export class CheckHistoryComponent {
  /** Oldest first. */
  readonly checks = input<HealthCheck[]>([]);
  readonly size = input<'compact' | 'full'>('compact');
  /** Faded (a disabled datasource: its history is old). */
  readonly dimmed = input(false);
  /** For the relative times under the full row. */
  readonly now = input(Date.now());
  readonly slots = input(10);

  protected readonly ready = signal(false);
  protected readonly placeholders = computed(() =>
    Array.from({ length: Math.max(0, this.slots() - this.checks().length) }, (_, i) => i));
  protected readonly summary = computed(() => checksSummary(this.checks()));
  protected readonly slowest = computed(() => {
    const times = this.checks().filter(c => c.status === 'UP' && c.responseTimeMs !== null).map(c => c.responseTimeMs!);
    return times.length ? Math.max(...times) : null;
  });
  protected readonly oldestLabel = computed(() => relativeTime(this.checks()[0]?.checkTime, this.now()));
  protected readonly latestLabel = computed(() => relativeTime(this.checks().at(-1)?.checkTime, this.now()));

  constructor() {
    // The row as first loaded appears as is; only checks that land later grow in.
    afterNextRender(() => this.ready.set(true));
  }

  protected fraction(check: HealthCheck): number {
    if (check.status !== 'UP' || check.responseTimeMs === null) {
      return 1;
    }
    // The slowest shown fills the row; a floor keeps a fast check visible. Under 50 ms everything reads as fast.
    const max = Math.max(this.slowest() ?? 0, 50);
    return 0.16 + 0.84 * Math.min(1, check.responseTimeMs / max);
  }

  protected tooltip(check: HealthCheck): string {
    const time = new Date(check.checkTime).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
    if (check.status === 'UP') {
      return `Up · ${check.responseTimeMs ?? '?'} ms · ${time}`;
    }
    return `Down · ${check.errorMessage ?? 'no answer'} · ${time}`;
  }
}
