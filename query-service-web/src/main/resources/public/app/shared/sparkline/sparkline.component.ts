import { afterNextRender, ChangeDetectionStrategy, Component, computed, DestroyRef, effect, ElementRef, inject, Injector, input, signal, untracked, viewChild } from '@angular/core';
import { prefersReducedMotion } from '../motion';

/** Height and width of the drawing, in SVG units (it scales to its box). */
const W = 100;
const H = 28;
/** Room kept under the line for failure ticks. */
const TICK_H = 4;
const MORPH_MS = 450;

/**
 * The points of a line through `values` (null = gap), scaled to the box: x spread evenly, y from 0 to the largest
 * value (at least `minMax`, so a quiet route draws a low line rather than a full-height one). Pure, unit tested.
 */
export function sparkPoints(values: (number | null)[], minMax = 1): ({ x: number; y: number } | null)[] {
  const max = Math.max(minMax, ...values.map(v => v ?? 0));
  const step = values.length > 1 ? W / (values.length - 1) : 0;
  const top = 2;
  const bottom = H - TICK_H - 1;
  return values.map((v, i) => v === null ? null
    : { x: values.length > 1 ? i * step : W / 2, y: bottom - (v / max) * (bottom - top) });
}

/** SVG path data for the line (segments broken at gaps) and the area under it. */
export function sparkPaths(values: (number | null)[], minMax = 1): { line: string; area: string } {
  const points = sparkPoints(values, minMax);
  const bottom = H - TICK_H - 1;
  let line = '';
  let area = '';
  let run: { x: number; y: number }[] = [];
  const flush = () => {
    if (run.length) {
      const d = run.map((p, i) => `${i ? 'L' : 'M'}${p.x.toFixed(2)},${p.y.toFixed(2)}`).join('');
      line += d;
      area += `${d}L${run[run.length - 1].x.toFixed(2)},${bottom}L${run[0].x.toFixed(2)},${bottom}Z`;
    }
    run = [];
  };
  for (const p of points) {
    if (p) {
      run.push(p);
    } else {
      flush();
    }
  }
  flush();
  return { line, area };
}

/** The window moved on by one minute: the old values, but the first, are the new values but the last. */
export function isShift(from: (number | null)[], next: (number | null)[]): boolean {
  return from.length === next.length && from.length > 2 && from.slice(1).every((v, i) => v === next[i])
    && !from.every((v, i) => v === next[i]);
}

/** "Calls per minute, last 30 minutes: between 0 and 14, now 12; failures in 2 minutes." */
export function sparkSummary(values: (number | null)[], failures: (number | null)[], unit: string): string {
  const known = values.filter((v): v is number => v !== null);
  if (!known.length) {
    return `No ${unit} recorded yet`;
  }
  const round = (v: number) => (v < 10 ? Math.round(v * 10) / 10 : Math.round(v)).toString();
  const failing = failures.filter(f => (f ?? 0) > 0).length;
  return `${unit[0].toUpperCase()}${unit.slice(1)} per minute, last ${values.length} minutes: between `
    + `${round(Math.min(...known))} and ${round(Math.max(...known))}, now ${round(known[known.length - 1])}`
    + (failing ? `; failures in ${failing} ${failing === 1 ? 'minute' : 'minutes'}` : '');
}

/**
 * A small trend line: calls per minute over the last half hour, with a red tick under each minute that had
 * failures. When new values arrive the line morphs to them (a new minute slides in) unless the viewer prefers
 * reduced motion. Plain SVG: a page can draw dozens without a chart library.
 */
@Component({
  selector: 'qtt-sparkline',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'qtt-sparkline',
    role: 'img',
    '[attr.aria-label]': 'summary()',
    '[class.is-empty]': '!hasData()',
  },
  template: `
    <svg [attr.viewBox]="'0 0 ' + width + ' ' + height" preserveAspectRatio="none" aria-hidden="true" focusable="false">
      @if (hasData()) {
        <g #slide>
          <path class="qtt-sparkline__area" [attr.d]="paths().area"></path>
          <path class="qtt-sparkline__line" [attr.d]="paths().line" vector-effect="non-scaling-stroke"></path>
          @for (tick of ticks(); track tick.x) {
            <rect class="qtt-sparkline__fail" [attr.x]="tick.x - 0.9" [attr.y]="height - tickHeight" width="1.8"
              [attr.height]="tickHeight"></rect>
          }
        </g>
      } @else {
        <line class="qtt-sparkline__none" x1="0" [attr.y1]="height - tickHeight - 1" [attr.x2]="width"
          [attr.y2]="height - tickHeight - 1" vector-effect="non-scaling-stroke"></line>
      }
    </svg>
  `,
  styles: [`
    :host { display: inline-block; width: 96px; height: 28px; vertical-align: middle; }
    svg { display: block; width: 100%; height: 100%; overflow: visible; }
    .qtt-sparkline__line { fill: none; stroke: var(--qtt-spark-color, var(--mat-sys-primary)); stroke-width: 1.5;
      stroke-linejoin: round; stroke-linecap: round; }
    .qtt-sparkline__area { fill: var(--qtt-spark-color, var(--mat-sys-primary)); opacity: 0.12; }
    .qtt-sparkline__fail { fill: var(--qtt-bad, var(--mat-sys-error)); }
    .qtt-sparkline__none { stroke: var(--mat-sys-outline-variant); stroke-width: 1; stroke-dasharray: 2 3; }
  `],
})
export class SparklineComponent {
  /** Values per minute, oldest first; null where there is no value. */
  readonly values = input<(number | null)[]>([]);
  /** Failures per minute, aligned to `values`. */
  readonly failures = input<(number | null)[]>([]);
  /** What the values count, for the spoken summary. */
  readonly unit = input('calls');
  /**
   * The value the full height stands for at least: a route with a call or two a minute draws a low line, not one
   * that fills the box with noise.
   */
  readonly minScale = input(1);

  readonly width = W;
  readonly height = H;
  readonly tickHeight = TICK_H;

  /** What is drawn: the values, or a frame of the morph towards them. */
  private readonly drawn = signal<(number | null)[]>([]);
  private frame = 0;
  private readonly slide = viewChild<ElementRef<SVGGElement>>('slide');
  private readonly injector = inject(Injector);

  readonly hasData = computed(() => this.drawn().some(v => v !== null));
  readonly paths = computed(() => sparkPaths(this.drawn(), this.minScale()));
  readonly ticks = computed(() => {
    const failures = this.failures();
    const step = failures.length > 1 ? W / (failures.length - 1) : 0;
    return failures.map((f, i) => ({ f, x: failures.length > 1 ? i * step : W / 2 })).filter(t => (t.f ?? 0) > 0);
  });
  readonly summary = computed(() => sparkSummary(this.values(), this.failures(), this.unit()));

  constructor() {
    effect(() => {
      const next = this.values();
      untracked(() => this.morphTo(next));
    });
    inject(DestroyRef).onDestroy(() => cancelAnimationFrame(this.frame));
  }

  private morphTo(next: (number | null)[]): void {
    cancelAnimationFrame(this.frame);
    const from = this.drawn();
    if (!from.length || prefersReducedMotion() || typeof requestAnimationFrame !== 'function') {
      this.drawn.set(next);
      return;
    }
    // A new minute shifts the window by one: draw the new window and slide it in from one step to the right
    if (isShift(from, next)) {
      this.drawn.set(next);
      const step = W / (next.length - 1);
      afterNextRender(() => this.slide()?.nativeElement.animate?.(
        [{ transform: `translateX(${step}px)` }, { transform: 'translateX(0)' }],
        { duration: MORPH_MS, easing: 'cubic-bezier(0.16, 1, 0.3, 1)' }), { injector: this.injector });
      return;
    }
    if (from.length !== next.length) {
      this.drawn.set(next);
      return;
    }
    const shifted = from;
    const start = performance.now();
    const step = (now: number) => {
      const t = Math.min(1, Math.max(0, (now - start) / MORPH_MS));
      const eased = 1 - Math.pow(1 - t, 3);
      this.drawn.set(next.map((v, i) => {
        const a = shifted[i];
        return v === null || a === null ? v : a + (v - a) * eased;
      }));
      if (t < 1) {
        this.frame = requestAnimationFrame(step);
      }
    };
    this.frame = requestAnimationFrame(step);
  }
}
