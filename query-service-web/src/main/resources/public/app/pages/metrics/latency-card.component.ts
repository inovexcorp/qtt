import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ChartData, ChartOptions, Plugin } from 'chart.js';
import { RouteMetric } from '../../core/models/metric-data';
import { ChartComponent } from './chart.component';
import { MetricsStatusComponent } from './metrics-status.component';
import { latencyRanges } from './metrics-derive';
import { categoryScale, FONT_FAMILY, formatMs, htmlTooltip, INK, motion, routeTick, tooltipOptions,
  valueScale } from './chart-theme';

const MAX_ROUTES = 10;
const ROW_PX = 34;
const AXIS_PX = 36;

/** The average, printed past the end of each range (the tooltip has min and max). */
const averagesPlugin: Plugin<'bar'> = {
  id: 'qttLatencyAverages',
  afterDatasetsDraw(chart) {
    const ranges = chart.getDatasetMeta(0);
    const means = chart.data.datasets[1]?.data as number[] | undefined;
    if (!means) {
      return;
    }
    const ctx = chart.ctx;
    ctx.save();
    ctx.font = `500 12px ${FONT_FAMILY}`;
    ctx.fillStyle = INK.text;
    ctx.textBaseline = 'middle';
    ranges.data.forEach((bar, i) => {
      const { x, y } = bar.getProps(['x', 'y']) as { x: number; y: number }; // where the bar's end is now, mid-animation too
      ctx.fillText(`${formatMs(means[i])} avg`, x + 10, y);
    });
    ctx.restore();
  },
};

/** Processing time per route: the min-max range since the route started, with the average marked. */
@Component({
  selector: 'qtt-latency-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ChartComponent, MetricsStatusComponent],
  template: `
    <section class="metrics-card" aria-labelledby="latency-title">
      <header class="metrics-card__header">
        <div class="metrics-card__heading">
          <h2 id="latency-title" class="metrics-card__title">Processing time by route</h2>
          <p class="metrics-card__subtitle">{{ subtitle() }}</p>
        </div>
      </header>
      @if (mode() === null) {
        <ul class="metrics-legend" aria-label="Series">
          <li><span class="metrics-legend__key metrics-legend__key--range" [style.--key-color]="ink.primarySoft"></span>Min to max</li>
          <li><span class="metrics-legend__key metrics-legend__key--dot" [style.--key-color]="ink.primary"></span>Average</li>
        </ul>
      }
      <div class="metrics-card__body" [style.height.px]="height()">
        @if (mode(); as mode) {
          <app-metrics-status [mode]="mode" emptyIcon="timer"
            [emptyTitle]="routes()?.length ? 'No exchanges yet' : 'No routes yet'"
            [emptyDetail]="routes()?.length ? 'Processing times appear once a route has handled a query.' : 'Routes appear here once they are created.'" [retryable]="false"></app-metrics-status>
        } @else {
          <qtt-chart type="bar" [data]="chartData()" [options]="chartOptions" [plugins]="plugins"
            label="Minimum, average and maximum processing time by route"></qtt-chart>
        }
      </div>
    </section>
  `,
})
export class LatencyCardComponent {
  /** Routes in scope; null while loading. */
  readonly routes = input.required<RouteMetric[] | null>();
  readonly failed = input(false);

  protected readonly ink = INK;
  protected readonly plugins = [averagesPlugin];

  private readonly ranges = computed(() => latencyRanges(this.routes() ?? [], MAX_ROUTES));

  protected readonly mode = computed<'loading' | 'error' | 'empty' | null>(() => {
    if (this.routes() === null) {
      return this.failed() ? 'error' : 'loading';
    }
    return this.ranges().ranges.length > 0 ? null : 'empty';
  });

  protected readonly subtitle = computed(() => {
    const { idle, hidden } = this.ranges();
    const notes = ['Since each route started, slowest first'];
    if (hidden > 0) {
      notes.push(`${hidden} more in the table`);
    }
    if (idle > 0) {
      notes.push(`${idle} without traffic not shown`);
    }
    return notes.join(' · ');
  });

  protected readonly height = computed(() =>
    this.mode() === null ? Math.max(3, this.ranges().ranges.length) * ROW_PX + AXIS_PX : 240);

  protected readonly chartData = computed<ChartData<'bar'>>(() => {
    const { ranges } = this.ranges();
    return {
      labels: ranges.map(r => r.route),
      datasets: [
        {
          label: 'Range',
          data: ranges.map(r => [r.min, r.max] as [number, number]),
          backgroundColor: INK.primarySoft,
          hoverBackgroundColor: '#c49cff',
          borderRadius: 4,
          borderSkipped: false,
          maxBarThickness: 8,
          categoryPercentage: 0.8,
          grouped: false,
          order: 2, // under the average dots (a lower order draws on top)
        },
        {
          type: 'line',
          label: 'Average',
          data: ranges.map(r => r.mean),
          showLine: false,
          pointRadius: 5,
          pointHoverRadius: 7,
          pointHitRadius: 14,
          pointBackgroundColor: INK.primary,
          pointBorderColor: INK.surface,
          pointBorderWidth: 2,
          pointHoverBorderWidth: 2,
          order: 1,
        } as never,
      ],
    };
  });

  protected readonly chartOptions: ChartOptions<'bar'> = {
    ...motion(),
    indexAxis: 'y',
    interaction: { mode: 'index', axis: 'y', intersect: false },
    layout: { padding: { right: 84 } },
    scales: {
      x: valueScale(formatMs),
      y: { ...categoryScale(), ticks: { color: INK.text, padding: 8, autoSkip: false, callback: routeTick } },
    },
    plugins: {
      tooltip: tooltipOptions(htmlTooltip(tooltip => {
        const points = tooltip.dataPoints;
        const [min, max] = (points.find(p => p.dataset.label === 'Range')?.raw as [number, number]) ?? [0, 0];
        const mean = Number(points.find(p => p.dataset.label === 'Average')?.raw ?? 0);
        return {
          title: String(points[0]?.label ?? ''),
          rows: [
            { value: formatMs(mean), label: 'average', color: INK.primary, key: 'dot' },
            { value: formatMs(min), label: 'fastest', color: INK.primarySoft, key: 'square' },
            { value: formatMs(max), label: 'slowest', color: INK.primarySoft, key: 'square' },
          ],
        };
      })),
    },
  } as ChartOptions<'bar'>;
}
