import { afterNextRender, ChangeDetectionStrategy, Component, computed, effect, ElementRef, inject, Injector, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { NgTemplateOutlet } from '@angular/common';
import { filter, interval, map, startWith } from 'rxjs';
import { MatIcon } from '@angular/material/icon';
import { MatAnchor, MatButton, MatIconButton } from '@angular/material/button';
import { MatButtonToggle, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatSelect } from '@angular/material/select';
import { MatOption } from '@angular/material/core';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { MatTooltip } from '@angular/material/tooltip';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { AuthService } from '../../core/services/auth.service';
import { DatasourceLiveService } from '../../core/services/datasource-live.service';
import { MetricsLiveService } from '../../core/services/metrics-live.service';
import { LiveHub } from '../../core/services/live-hub';
import { IfRoleDirective } from '../../shared/if-role.directive';
import { LiveIndicatorComponent } from '../../shared/live-indicator/live-indicator.component';
import { SparklineComponent } from '../../shared/sparkline/sparkline.component';
import { relativeTime, statusLabel, statusTone } from '../../shared/datasource-health/health-status';
import { endpointUrl } from '../../shared/query-endpoint';
import { ADD_ROUTE_LINK, routeLink } from '../../shared/route-links';
import { liveText } from '../datasources/datasources.component';
import { RouteActionsService, RouteTarget } from './route-actions.service';
import { RoutesLiveService } from './routes-live.service';
import {
  AccessFilter, countStates, filterRows, formatErrorRate, formatMs, formatRate, groupByDatasource, headline,
  NO_FILTER, recoveries, Recovery, RouteFilter, RouteGroup, RouteRow, RouteSort, RouteState, sortRows, StateFilter,
} from './routes-derive';

/** What a viewer's list looks like, kept in their browser: the sort, grouping, and collapsed groups. */
interface ViewPrefs {
  sort: RouteSort;
  grouped: boolean;
  collapsed: string[];
}

const PREFS_KEY = 'qtt.routes.view';
const DEFAULT_PREFS: ViewPrefs = { sort: 'name', grouped: false, collapsed: [] };

export function readPrefs(storage: Pick<Storage, 'getItem'> | null): ViewPrefs {
  try {
    const stored = JSON.parse(storage?.getItem(PREFS_KEY) ?? 'null') as Partial<ViewPrefs> | null;
    const sorts: RouteSort[] = ['name', 'status', 'busiest', 'slowest', 'errors'];
    return {
      sort: sorts.includes(stored?.sort as RouteSort) ? stored!.sort! : DEFAULT_PREFS.sort,
      grouped: stored?.grouped === true,
      collapsed: Array.isArray(stored?.collapsed) ? stored!.collapsed!.filter(c => typeof c === 'string') : [],
    };
  } catch {
    return DEFAULT_PREFS;
  }
}

function storage(): Storage | null {
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

/** The split bar's segments, in display order. */
const SEGMENTS: { key: RouteState; label: string }[] = [
  { key: 'running', label: 'Running' },
  { key: 'blocked', label: 'Blocked' },
  { key: 'stopped', label: 'Stopped' },
];

/**
 * The routes: one sentence and a bar for the state of all of them, filters, then a row per route with its status,
 * its datasource's health, who may call it and its traffic over the last half hour, with start/stop, copy URL and
 * a menu at hand. Rows can be selected for a bulk start, stop or delete. Live: states, health and traffic change in
 * place as the server pushes them (see {@link RoutesLiveService}).
 *
 * A route's page renders under it (router outlet) and shares its live connection.
 */
@Component({
  selector: 'app-routes',
  templateUrl: './routes.component.html',
  styleUrls: ['./routes.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [LiveHub, DatasourceLiveService, MetricsLiveService, RoutesLiveService],
  imports: [RouterLink, RouterOutlet, NgTemplateOutlet, MatIcon, MatAnchor, MatButton, MatIconButton, MatButtonToggleGroup,
    MatButtonToggle, MatFormField, MatLabel, MatSuffix, MatInput, MatSelect, MatOption, MatCheckbox, MatSlideToggle,
    MatTooltip, MatMenu, MatMenuItem, MatMenuTrigger, IfRoleDirective, LiveIndicatorComponent, SparklineComponent],
})
export class RoutesComponent {
  protected readonly live = inject(RoutesLiveService);
  protected readonly actions = inject(RouteActionsService);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  private readonly url = toSignal(this.router.events.pipe(
    filter(event => event instanceof NavigationEnd),
    map(() => this.router.url),
    startWith(this.router.url),
  ), { initialValue: this.router.url });
  /** The list shows only at /routes itself, not under a route's page. */
  protected readonly listActive = computed(() => this.url().split(/[?#]/)[0] === '/routes');

  protected readonly now = toSignal(interval(30000).pipe(map(() => Date.now())), { initialValue: Date.now() });
  /** Set after the first render: entrances animate only for changes, not on load. */
  protected readonly ready = signal(false);
  private readonly user = toSignal(this.auth.user$, { initialValue: null });
  protected readonly isEditor = computed(() => (this.user(), this.auth.hasRole('editor')));

  // --- Filters and view ---------------------------------------------------------------------------------------------

  protected readonly filter = signal<RouteFilter>(NO_FILTER);
  private readonly prefs = signal<ViewPrefs>(readPrefs(storage()));
  protected readonly sort = computed(() => this.prefs().sort);
  protected readonly grouped = computed(() => this.prefs().grouped);

  protected readonly rows = this.live.rows;
  protected readonly counts = computed(() => countStates(this.rows() ?? []));
  /** Counts for the toggles: every filter but the status one applies. */
  protected readonly toggleCounts = computed(() =>
    countStates(filterRows(this.rows() ?? [], this.filter(), true)));
  protected readonly headline = computed(() => headline(this.counts()));
  protected readonly segments = computed(() => SEGMENTS.map(s => ({ ...s, count: this.counts()[s.key] })));
  protected readonly datasourceIds = computed(() => [...new Set((this.rows() ?? []).map(r => r.datasourceId))].sort());
  protected readonly visible = computed(() => sortRows(filterRows(this.rows() ?? [], this.filter()), this.sort()));
  protected readonly groups = computed<RouteGroup[]>(() => groupByDatasource(this.visible()));
  protected readonly recoveries = computed<Recovery[]>(() => recoveries(this.rows() ?? []));
  protected readonly filtered = computed(() => {
    const f = this.filter();
    return f.term.trim() !== '' || f.state !== 'all' || f.datasource !== '' || f.access !== 'all';
  });

  // --- Selection and work in progress -------------------------------------------------------------------------------

  protected readonly selected = signal<ReadonlySet<string>>(new Set());
  protected readonly selectedRows = computed(() => (this.rows() ?? []).filter(r => this.selected().has(r.id)));
  protected readonly allVisibleSelected = computed(() =>
    this.visible().length > 0 && this.visible().every(r => this.selected().has(r.id)));
  protected readonly someVisibleSelected = computed(() =>
    !this.allVisibleSelected() && this.visible().some(r => this.selected().has(r.id)));
  /** Routes an action is running on. */
  protected readonly pending = signal<ReadonlySet<string>>(new Set());
  /** A route that just appeared because of this page (a copy): highlighted once. */
  protected readonly highlight = signal<string | null>(null);

  protected readonly skeletons = [0, 1, 2, 3, 4];

  constructor() {
    afterNextRender(() => this.ready.set(true));

    // Forget selected routes that are gone (deleted here or elsewhere)
    effect(() => {
      const ids = new Set((this.rows() ?? []).map(r => r.id));
      untracked(() => {
        const kept = [...this.selected()].filter(id => ids.has(id));
        if (kept.length !== this.selected().size) {
          this.selected.set(new Set(kept));
        }
      });
    });
    // Back on the list from a route's page: show what changed there
    effect(() => {
      if (this.listActive()) {
        untracked(() => void this.live.load());
      }
    });
  }

  // --- Filters ------------------------------------------------------------------------------------------------------

  protected setFilter(change: Partial<RouteFilter>): void {
    this.filter.update(f => ({ ...f, ...change }));
  }

  protected setState(state: StateFilter): void {
    this.setFilter({ state });
  }

  protected setAccess(access: AccessFilter): void {
    this.setFilter({ access });
  }

  protected clearFilters(): void {
    this.filter.set(NO_FILTER);
  }

  protected setSort(sort: RouteSort): void {
    this.savePrefs({ sort });
  }

  protected setGrouped(grouped: boolean): void {
    this.savePrefs({ grouped });
  }

  protected isCollapsed(group: RouteGroup): boolean {
    return this.prefs().collapsed.includes(group.datasourceId);
  }

  protected toggleGroup(group: RouteGroup): void {
    const collapsed = this.prefs().collapsed;
    this.savePrefs({ collapsed: collapsed.includes(group.datasourceId)
      ? collapsed.filter(c => c !== group.datasourceId) : [...collapsed, group.datasourceId] });
  }

  private savePrefs(change: Partial<ViewPrefs>): void {
    this.prefs.update(p => ({ ...p, ...change }));
    try {
      storage()?.setItem(PREFS_KEY, JSON.stringify(this.prefs()));
    } catch {
      // Not kept (private window, storage blocked): the view still changes
    }
  }

  // --- Selection ----------------------------------------------------------------------------------------------------

  protected toggleSelected(id: string, on?: boolean): void {
    this.selected.update(set => {
      const next = new Set(set);
      if (on ?? !next.has(id)) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return next;
    });
  }

  protected toggleAllVisible(on: boolean): void {
    this.selected.update(set => {
      const next = new Set(set);
      this.visible().forEach(r => on ? next.add(r.id) : next.delete(r.id));
      return next;
    });
  }

  protected toggleGroupSelected(group: RouteGroup, on: boolean): void {
    this.selected.update(set => {
      const next = new Set(set);
      group.rows.forEach(r => on ? next.add(r.id) : next.delete(r.id));
      return next;
    });
  }

  protected groupSelection(group: RouteGroup): 'all' | 'some' | 'none' {
    const count = group.rows.filter(r => this.selected().has(r.id)).length;
    return count === 0 ? 'none' : count === group.rows.length ? 'all' : 'some';
  }

  protected clearSelection(): void {
    this.selected.set(new Set());
  }

  // --- Actions ------------------------------------------------------------------------------------------------------

  private target(row: RouteRow): RouteTarget {
    return { id: row.id, startBlocked: row.startBlocked, perMinute: row.traffic.perMinute };
  }

  protected async start(rows: RouteRow[]): Promise<void> {
    const result = await this.withPending(rows, () => this.actions.start(rows.map(r => this.target(r))));
    result.done.forEach(id => this.live.patch(id, { status: 'Started', stopReason: null, stoppedAt: null }));
    this.afterAction(result.done);
  }

  protected async stop(rows: RouteRow[]): Promise<void> {
    const result = await this.withPending(rows, () => this.actions.stop(rows.map(r => this.target(r))));
    if (result) {
      result.done.forEach(id => this.live.patch(id, { status: 'Stopped', stopReason: 'MANUAL', stoppedAt: Date.now() }));
      this.afterAction(result.done);
    }
  }

  protected async remove(rows: RouteRow[]): Promise<void> {
    const result = await this.withPending(rows, () => this.actions.delete(rows.map(r => this.target(r))));
    if (result) {
      this.live.routes.update(list => list?.filter(r => !result.done.includes(r.routeId)) ?? list);
      this.afterAction(result.done);
    }
  }

  protected async clone(row: RouteRow): Promise<void> {
    const copy = await this.withPending([row], () => this.actions.clone(row.id));
    if (copy) {
      await this.live.load();
      this.highlight.set(copy);
      afterNextRender(() => this.host.nativeElement.querySelector(`[data-route-id="${CSS.escape(copy)}"]`)
        ?.scrollIntoView({ block: 'nearest', behavior: 'smooth' }), { injector: this.injector });
      setTimeout(() => this.highlight.update(h => h === copy ? null : h), 2400);
    }
  }

  protected startRecovery(recovery: Recovery): void {
    const rows = (this.rows() ?? []).filter(r => recovery.routeIds.includes(r.id));
    void this.start(rows);
  }

  protected reviewRecovery(recovery: Recovery): void {
    this.filter.set({ ...NO_FILTER, state: 'awaiting-restart', datasource: recovery.datasourceId });
  }

  protected copyUrl(row: RouteRow): void {
    this.actions.copy(this.endpoint(row));
  }

  protected bulkStart(): void {
    // Blocked ones too: the result says they were skipped and why
    void this.start(this.selectedRows().filter(r => !r.started));
  }

  protected bulkStop(): void {
    void this.stop(this.selectedRows().filter(r => r.started));
  }

  protected bulkDelete(): void {
    void this.remove(this.selectedRows());
  }

  /** Selected routes that can start now (stopped, on a datasource that can serve them). */
  protected readonly selectedStopped = computed(() => this.selectedRows().filter(r => !r.started && !r.startBlocked).length);
  protected readonly selectedStarted = computed(() => this.selectedRows().filter(r => r.started).length);

  private async withPending<T>(rows: RouteRow[], work: () => Promise<T>): Promise<T> {
    const ids = rows.map(r => r.id);
    this.pending.update(set => new Set([...set, ...ids]));
    try {
      return await work();
    } finally {
      this.pending.update(set => new Set([...set].filter(id => !ids.includes(id))));
    }
  }

  private afterAction(done: string[]): void {
    this.selected.update(set => new Set([...set].filter(id => !done.includes(id))));
    void this.live.load();
  }

  // --- Display ------------------------------------------------------------------------------------------------------

  protected readonly routeLink = routeLink;
  protected readonly addRouteLink = ADD_ROUTE_LINK;
  protected readonly formatRate = formatRate;
  protected readonly formatErrorRate = formatErrorRate;
  protected readonly formatMs = formatMs;
  protected readonly statusLabel = statusLabel;
  protected readonly statusTone = statusTone;

  /** Whether the row has traffic to show: a stopped or blocked route without calls in the window shows dashes. */
  protected hasTraffic(row: RouteRow): boolean {
    return row.state === 'running' || row.traffic.calls > 0;
  }

  protected endpoint(row: RouteRow): string {
    return endpointUrl(this.live.baseUrl(), row.id);
  }

  protected stateLabel(row: RouteRow): string {
    if (row.state === 'running') {
      return 'Running';
    }
    if (row.state === 'blocked') {
      return row.datasourceStatus === 'DOWN' ? 'Blocked: datasource down' : 'Blocked: datasource disabled';
    }
    return row.awaitingRestart ? 'Stopped: waiting to be restarted' : 'Stopped';
  }

  /** The second line: why it is blocked or stopped, with when; otherwise the description. */
  protected detail(row: RouteRow): string {
    if (!row.note) {
      return row.description;
    }
    const when = row.stoppedAt !== null && row.stopReason === 'HEALTH_CHECK'
      ? relativeTime(new Date(row.stoppedAt).toISOString(), this.now()) : '';
    return when ? `${row.note} · ${when}` : row.note;
  }

  protected since(recovery: Recovery): string {
    return recovery.since === null ? '' : relativeTime(new Date(recovery.since).toISOString(), this.now());
  }

  protected readonly liveText = computed(() => liveText(this.live.status(), this.live.metrics.transport(),
    this.live.failed(), this.live.updatedAt(), this.now()));
  protected readonly liveAnnouncement = computed(() => ({
    connecting: 'Connecting to live route status', live: 'Route status and traffic are live',
    stale: 'Connection lost; reconnecting', paused: 'Live updates paused',
  } as Record<string, string>)[this.live.status()] ?? '');

  protected togglePause(): void {
    if (this.live.status() === 'paused') {
      this.live.metrics.resume();
    } else {
      this.live.metrics.pause();
    }
  }
}
