import { afterNextRender, ChangeDetectionStrategy, Component, DestroyRef, effect, ElementRef, inject, input, NgZone,
  untracked, viewChild } from '@angular/core';
import { Chart, ChartData, ChartDataset, ChartOptions, ChartType, Plugin } from 'chart.js';
import { applyChartDefaults, prefersReducedMotion } from './chart-theme';

/**
 * A Chart.js chart. On new data it updates the existing chart in place (datasets matched by label), so values morph
 * from where they were instead of the chart being rebuilt and replaying its entrance: a live refresh reads as change.
 *
 * Fills its host; give the host a height. The canvas is an image for assistive technology (`label`); every value is
 * also in the page's route table.
 */
@Component({
  selector: 'qtt-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<canvas #canvas role="img" [attr.aria-label]="label()"></canvas>`,
  host: { class: 'qtt-chart' },
})
export class ChartComponent<T extends ChartType = ChartType> {
  readonly type = input.required<T>();
  readonly data = input.required<ChartData<T>>();
  readonly options = input<ChartOptions<T>>({} as ChartOptions<T>);
  readonly plugins = input<Plugin<T>[]>([]);
  readonly label = input.required<string>();

  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private readonly zone = inject(NgZone);
  private chart?: Chart<T>;

  constructor() {
    applyChartDefaults();
    afterNextRender(() => {
      this.zone.runOutsideAngular(() => {
        this.chart = new Chart(this.canvas().nativeElement, {
          type: untracked(this.type),
          data: cloneData(untracked(this.data)),
          options: untracked(this.options),
          plugins: untracked(this.plugins),
        });

      });
    });
    effect(() => {
      const data = this.data();
      const options = this.options();
      const chart = this.chart;
      if (chart) {
        this.zone.runOutsideAngular(() => {
          // New values for the same rows and series morph. Different rows or series (another datasource, the
          // history window moving on a minute) are different things: a bar morphing from one route into another
          // would mislead, so those redraw in place. (A datasource change is already marked by the page dimming
          // the old data and releasing it when the new data lands.)
          const sameShape = sameKeys(chart.data.labels, data.labels)
            && sameKeys(chart.data.datasets.map(d => d.label), data.datasets.map(d => d.label));
          chart.options = options;
          reconcile(chart.data, data);
          chart.update(sameShape && !prefersReducedMotion() ? undefined : 'none');
        });
      }
    });
    inject(DestroyRef).onDestroy(() => this.chart?.destroy());
  }
}

function sameKeys(a: readonly unknown[] | undefined, b: readonly unknown[] | undefined): boolean {
  return (a?.length ?? 0) === (b?.length ?? 0) && (a ?? []).every((value, i) => value === b![i]);
}

function cloneData<T extends ChartType>(data: ChartData<T>): ChartData<T> {
  return { labels: [...(data.labels ?? [])], datasets: data.datasets.map(d => ({ ...d })) } as ChartData<T>;
}

/**
 * Brings the chart's data to `next` while keeping each dataset object (Chart.js keys its animation state on dataset
 * identity): datasets are matched by label, falling back to position.
 */
function reconcile<T extends ChartType>(current: ChartData<T>, next: ChartData<T>): void {
  current.labels = [...(next.labels ?? [])];
  const existing = current.datasets as ChartDataset<T>[];
  const used = new Set<ChartDataset<T>>();
  current.datasets = next.datasets.map((spec, i) => {
    const match = existing.find(d => !used.has(d) && d.label !== undefined && d.label === spec.label)
      ?? (existing[i] && !used.has(existing[i]) && existing[i].label === undefined ? existing[i] : undefined);
    if (!match) {
      return { ...spec };
    }
    used.add(match);
    for (const key of Object.keys(match)) {
      if (!(key in spec)) {
        delete (match as unknown as Record<string, unknown>)[key];
      }
    }
    return Object.assign(match, spec);
  }) as typeof current.datasets;
}
