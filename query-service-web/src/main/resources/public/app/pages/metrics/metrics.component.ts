import { ChangeDetectionStrategy, Component, computed, effect, ElementRef, inject, signal, untracked, viewChild,
  ViewEncapsulation } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, interval, map, of } from 'rxjs';
import { MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatOption, MatSelect } from '@angular/material/select';
import { MatInput } from '@angular/material/input';
import { MatIcon } from '@angular/material/icon';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { MatCell, MatCellDef, MatColumnDef, MatHeaderCell, MatHeaderCellDef, MatHeaderRow, MatHeaderRowDef, MatNoDataRow,
  MatRow, MatRowDef, MatTable, MatTableDataSource } from '@angular/material/table';
import { MatSort, MatSortHeader } from '@angular/material/sort';
import { MatPaginator } from '@angular/material/paginator';
import { DecimalPipe } from '@angular/common';
import { RouteMetric } from '../../core/models/metric-data';
import { DatasourceService } from '../../core/services/datasource.service';
import { MetricsLiveService } from '../../core/services/metrics-live.service';
import { inScope, summarize } from './metrics-derive';
import { prefersReducedMotion } from './chart-theme';
import { MetricsStatusComponent } from './metrics-status.component';
import { TrendCardComponent } from './trend-card.component';
import { ExchangesCardComponent } from './exchanges-card.component';
import { LatencyCardComponent } from './latency-card.component';
import { TweenNumberDirective } from './tween-number.directive';

/** A row of the routes table. */
export interface RouteRow {
  route: string;
  state: string;
  completed: number;
  failed: number;
  total: number;
  successRate: number | null;
  mean: number | null;
  min: number | null;
  max: number | null;
  uptime: string;
}

/**
 * The metrics dashboard: summary tiles, a per-minute trend, exchanges and processing time by route, and the routes
 * table, all scoped by one filter row (datasource, routes) and kept live by {@link MetricsLiveService}.
 */
@Component({
  selector: 'app-metrics',
  templateUrl: './metrics.component.html',
  styleUrls: ['./metrics.component.scss'],
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [MetricsLiveService],
  imports: [MatFormField, MatLabel, MatSuffix, MatSelect, MatOption, MatInput, MatIcon, MatButton, MatIconButton,
    MatTooltip, MatTable, MatColumnDef, MatHeaderCellDef, MatHeaderCell, MatCellDef, MatCell, MatHeaderRowDef,
    MatHeaderRow, MatRowDef, MatRow, MatNoDataRow, MatSort, MatSortHeader, MatPaginator, DecimalPipe,
    MetricsStatusComponent, TrendCardComponent, ExchangesCardComponent, LatencyCardComponent, TweenNumberDirective],
})
export class MetricsComponent {
  protected readonly live = inject(MetricsLiveService);

  protected readonly datasources = toSignal(
    inject(DatasourceService).getDatasources().pipe(catchError(() => of([]))), { initialValue: [] });
  protected readonly datasource = signal('');
  protected readonly selectedRoutes = signal<string[]>([]);

  /** Route names in the current datasource scope, for the route filter. */
  protected readonly routeNames = computed(() => (this.live.routes() ?? []).map(r => r.route).sort());

  /** Routes after both filters; null until loaded. */
  protected readonly scoped = computed<RouteMetric[] | null>(() => {
    const routes = this.live.routes();
    return routes ? inScope(routes, this.selectedRoutes()) : null;
  });

  /** The history is for all routes; narrow it to the routes in scope (null = no narrowing). */
  protected readonly historyScope = computed<string[] | null>(() => {
    if (this.selectedRoutes().length > 0) {
      return this.selectedRoutes();
    }
    return this.datasource() && this.live.routes() ? this.routeNames() : null;
  });

  protected readonly summary = computed(() => {
    const routes = this.scoped();
    return routes ? summarize(routes) : null;
  });

  protected readonly stopped = computed(() => (this.scoped() ?? []).filter(r => r.state !== 'Started').length);

  /** Why a summary value is missing. */
  protected readonly missingReason = computed(() => {
    if (this.live.routesFailed()) {
      return 'Unavailable';
    }
    return this.summary()?.endpoints ? 'No exchanges yet' : 'No routes yet';
  });

  protected readonly avgParts = computed(() => {
    const ms = this.summary()?.avgProcessingMs;
    if (ms == null) {
      return null;
    }
    return ms >= 1000 ? { value: ms / 1000, unit: 's' } : { value: ms, unit: 'ms' };
  });

  protected readonly busiestShare = computed(() => {
    const summary = this.summary();
    const busiest = this.scoped()?.find(r => r.route === summary?.busiestRoute);
    if (!summary || !busiest || summary.totalExchanges === 0) {
      return null;
    }
    return { exchanges: busiest.exchangesTotal, share: (busiest.exchangesTotal / summary.totalExchanges) * 100 };
  });

  protected readonly failedTotal = computed(() =>
    (this.scoped() ?? []).reduce((sum, r) => sum + r.exchangesFailed, 0));

  // --- Live status ---------------------------------------------------------------------------------------------

  private readonly now = toSignal(interval(1000).pipe(map(() => Date.now())), { initialValue: Date.now() });

  protected readonly updatedAgo = computed(() => {
    const at = this.live.updatedAt();
    if (at === null) {
      return '';
    }
    const seconds = Math.max(0, Math.round((this.now() - at) / 1000));
    if (seconds < 5) {
      return 'just now';
    }
    return seconds < 60 ? `${seconds}s ago` : `${Math.floor(seconds / 60)} min ago`;
  });

  protected readonly statusText = computed(() => {
    switch (this.live.status()) {
      case 'connecting':
        return this.live.routesFailed() ? 'Not connected · retrying' : 'Connecting…';
      case 'live':
        return `Live · updated ${this.updatedAgo()}`;
      case 'stale':
        return `Couldn't refresh · last update ${this.updatedAgo()}`;
      case 'paused':
        return this.live.updatedAt() === null ? 'Paused' : `Paused · updated ${this.updatedAgo()}`;
    }
  });

  /** For screen readers: announced when the connection state changes, not on every tick of the clock. */
  protected readonly statusAnnouncement = computed(() => ({
    connecting: 'Connecting to metrics', live: 'Metrics are live', stale: 'Metrics could not refresh; retrying',
    paused: 'Live updates paused',
  })[this.live.status()]);

  private readonly ping = viewChild<ElementRef<HTMLElement>>('ping');

  // --- Table ---------------------------------------------------------------------------------------------------

  protected readonly columns = ['route', 'state', 'completed', 'failed', 'successRate', 'mean', 'range', 'uptime'];
  protected readonly table = new MatTableDataSource<RouteRow>([]);
  protected readonly tableMode = computed<'loading' | 'error' | 'empty' | null>(() => {
    const routes = this.scoped();
    if (routes === null) {
      return this.live.routesFailed() ? 'error' : 'loading';
    }
    return routes.length > 0 ? null : 'empty';
  });

  private readonly sort = viewChild(MatSort);
  private readonly paginator = viewChild(MatPaginator);

  constructor() {
    this.table.filterPredicate = (row, filter) => row.route.toLowerCase().includes(filter);
    this.table.sortingDataAccessor = (row, column) => {
      const value = row[column === 'range' ? 'max' : column as keyof RouteRow];
      return value === null ? -1 : value;
    };

    effect(() => {
      this.table.data = (this.scoped() ?? []).map(toRow);
    });
    effect(() => {
      this.table.sort = this.sort() ?? null;
      this.table.paginator = this.paginator() ?? null;
    });
    // One ping of the live dot when new data lands.
    effect(() => {
      const at = this.live.updatedAt();
      const ping = untracked(this.ping);
      if (at !== null && ping && !prefersReducedMotion()) {
        ping.nativeElement.animate(
          [{ transform: 'scale(1)', opacity: 0.6 }, { transform: 'scale(2.8)', opacity: 0 }],
          { duration: 900, easing: 'cubic-bezier(0.16, 1, 0.3, 1)' });
      }
    });
  }

  protected selectDatasource(datasource: string): void {
    this.datasource.set(datasource);
    this.selectedRoutes.set([]);
    this.live.setDatasource(datasource);
  }

  protected togglePause(): void {
    if (this.live.status() === 'paused') {
      this.live.resume();
    } else {
      this.live.pause();
    }
  }

  protected search(term: string): void {
    this.table.filter = term.trim().toLowerCase();
    this.table.paginator?.firstPage();
  }

  // Fixed precision, so a figure doesn't change width or drop its decimal while it counts to a new value.
  /** One decimal under 100 (70.7, 10.0), whole numbers from 100 (1,250). */
  protected readonly formatOneDecimal = (v: number) =>
    v.toLocaleString(undefined, v >= 100 ? { maximumFractionDigits: 0 } : { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  /** One decimal (97.9, 75.0), never rounded up to a perfect 100 it isn't; exactly 100 is "100". */
  protected readonly formatPercent = (v: number) => v === 100 ? '100'
    : Math.min(v, 99.9).toLocaleString(undefined, { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  protected readonly formatInteger = (v: number) => Math.round(v).toLocaleString();
}

function toRow(r: RouteMetric): RouteRow {
  const has = r.exchangesTotal > 0;
  return {
    route: r.route,
    state: r.state,
    completed: r.exchangesCompleted,
    failed: r.exchangesFailed,
    total: r.exchangesTotal,
    successRate: has ? (r.exchangesCompleted / r.exchangesTotal) * 100 : null,
    mean: has && r.meanProcessingTime >= 0 ? r.meanProcessingTime : null,
    min: has ? r.minProcessingTime : null,
    max: has ? r.maxProcessingTime : null,
    uptime: r.uptime,
  };
}
