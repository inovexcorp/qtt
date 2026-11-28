import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { ChartData, ChartOptions, Plugin } from 'chart.js';
import { MatButtonToggle, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { RouteMetric } from '../../core/models/metric-data';
import { ChartComponent } from './chart.component';
import { MetricsStatusComponent } from './metrics-status.component';
import { buildTrends, clockLabel, TrendLine, trendLines, TrendMetric } from './metrics-derive';
import { categoryScale, crosshairPlugin, formatMs, formatRate, htmlTooltip, INK, motion, ROUTE_COLORS,
  tooltipOptions, valueScale } from './chart-theme';

/** One line per route colour; routes past that are folded into one grey "other routes" line. */
const MAX_ROUTES = ROUTE_COLORS.length;

const METRICS: { id: TrendMetric; label: string; title: string; format: (v: number) => string }[] = [
  { id: 'exchanges', label: 'Exchanges', title: 'Exchanges per minute', format: formatRate },
  { id: 'failures', label: 'Failures', title: 'Failed exchanges per minute', format: formatRate },
  { id: 'avgTime', label: 'Avg time', title: 'Average processing time', format: formatMs },
];

/** Per-minute trend over the history window, one line per route (busiest first, the rest folded). */
@Component({
  selector: 'qtt-trend-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ChartComponent, MetricsStatusComponent, MatButtonToggleGroup, MatButtonToggle],
  template: `
    <section class="metrics-card metrics-card--wide" aria-labelledby="trend-title">
      <header class="metrics-card__header">
        <div class="metrics-card__heading">
          <h2 id="trend-title" class="metrics-card__title">{{ current().title }}</h2>
          <p class="metrics-card__subtitle">Last 30 minutes, sampled every minute</p>
        </div>
        <mat-button-toggle-group class="metrics-toggle" hideSingleSelectionIndicator aria-label="Trend metric"
          [value]="metric()" (change)="metric.set($event.value)">
          @for (m of metrics; track m.id) {
            <mat-button-toggle [value]="m.id">{{ m.label }}</mat-button-toggle>
          }
        </mat-button-toggle-group>
      </header>

      @if (lines().length > 1 && mode() === null) {
        <ul class="metrics-legend" aria-label="Routes">
          @for (line of lines(); track line.label) {
            <li><span class="metrics-legend__key metrics-legend__key--line" [style.--key-color]="colorOf(line)"></span>{{ line.label }}</li>
          }
        </ul>
      }

      <div class="metrics-card__body" style="height: 280px">
        @if (mode(); as mode) {
          <app-metrics-status [mode]="mode" emptyIcon="show_chart" [emptyTitle]="emptyTitle()"
            [emptyDetail]="emptyDetail()" [retryable]="false"></app-metrics-status>
        } @else {
          <qtt-chart type="line" [data]="chartData()" [options]="chartOptions()" [plugins]="plugins"
            [label]="current().title + ' by route, last 30 minutes'"></qtt-chart>
        }
      </div>
    </section>
  `,
})
export class TrendCardComponent {
  /** History snapshots (all routes); null while loading. */
  readonly history = input.required<RouteMetric[] | null>();
  /** Routes in scope (datasource and route filters); null = all. */
  readonly scope = input<string[] | null>(null);
  readonly failed = input(false);

  protected readonly metrics = METRICS;
  protected readonly metric = signal<TrendMetric>('exchanges');
  protected readonly current = computed(() => METRICS.find(m => m.id === this.metric())!);
  protected readonly plugins = [crosshairPlugin] as Plugin<'line'>[];

  /** Colour slot per route while it stays on the chart: a route keeps its colour when others come and go. */
  private readonly slots = new Map<string, number>();

  private readonly trends = computed(() => {
    const history = this.history();
    if (!history) {
      return null;
    }
    const scope = this.scope();
    return buildTrends(scope ? history.filter(s => scope.includes(s.route)) : history);
  });

  protected readonly lines = computed<TrendLine[]>(() => {
    const trends = this.trends();
    if (!trends) {
      return [];
    }
    const lines = trendLines(trends, this.metric(), MAX_ROUTES);
    this.assignSlots(lines);
    return lines;
  });

  protected readonly mode = computed<'loading' | 'error' | 'empty' | null>(() => {
    if (this.history() === null) {
      return this.failed() ? 'error' : 'loading';
    }
    const lines = this.lines();
    return lines.length > 0 && lines.some(l => l.values.some(v => v !== null)) ? null : 'empty';
  });

  protected readonly emptyTitle = computed(() =>
    (this.trends()?.times.length ?? 0) === 0 ? 'No history yet' : 'No exchanges in the last 30 minutes');
  protected readonly emptyDetail = computed(() =>
    (this.trends()?.times.length ?? 0) === 0
      ? 'A sample is taken every minute; trends appear after the second one.'
      : 'Average processing time needs at least one exchange in a minute.');

  protected colorOf(line: TrendLine): string {
    return line.other ? INK.other : ROUTE_COLORS[this.slots.get(line.label) ?? 0];
  }

  protected readonly chartData = computed<ChartData<'line'>>(() => {
    const lines = this.lines();
    const single = lines.length === 1;
    return {
      labels: (this.trends()?.times ?? []).map(clockLabel),
      datasets: lines.map(line => {
        const color = this.colorOf(line);
        return {
          label: line.label,
          data: line.values,
          borderColor: color,
          backgroundColor: single ? hexAlpha(color, 0.1) : color,
          fill: single ? 'origin' : false,
          borderWidth: 2,
          borderCapStyle: 'round',
          borderJoinStyle: 'round',
          cubicInterpolationMode: 'monotone',
          spanGaps: false,
          pointRadius: 0,
          pointHoverRadius: 5,
          pointHitRadius: 12,
          pointHoverBackgroundColor: color,
          pointHoverBorderColor: INK.surface,
          pointHoverBorderWidth: 2,
        };
      }),
    };
  });

  protected readonly chartOptions = computed<ChartOptions<'line'>>(() => {
    const format = this.current().format;
    return {
      ...motion(),
      interaction: { mode: 'index', intersect: false },
      layout: { padding: { top: 8, right: 8 } },
      scales: {
        x: categoryScale({ ticks: { maxTicksLimit: 8, maxRotation: 0, color: INK.textMuted, padding: 8 } }),
        y: valueScale(format),
      },
      plugins: {
        tooltip: tooltipOptions(htmlTooltip(tooltip => ({
          title: String(tooltip.title?.[0] ?? ''),
          rows: tooltip.dataPoints
            .filter(p => p.raw !== null)
            .sort((a, b) => Number(b.raw) - Number(a.raw))
            .map(p => ({
              value: format(Number(p.raw)),
              label: p.dataset.label ?? '',
              color: String(p.dataset.borderColor),
              key: 'line' as const,
            })),
        }))),
      },
    } as ChartOptions<'line'>;
  });

  private assignSlots(lines: TrendLine[]): void {
    const shown = new Set(lines.filter(l => !l.other).map(l => l.label));
    for (const route of [...this.slots.keys()]) {
      if (!shown.has(route)) {
        this.slots.delete(route);
      }
    }
    const taken = new Set(this.slots.values());
    for (const route of shown) {
      if (!this.slots.has(route)) {
        const free = ROUTE_COLORS.findIndex((_, i) => !taken.has(i));
        this.slots.set(route, free);
        taken.add(free);
      }
    }
  }
}

function hexAlpha(hex: string, alpha: number): string {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${alpha})`;
}
