import {
  afterNextRender, ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, Injector, signal, untracked,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { firstValueFrom, map } from 'rxjs';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { MatTooltip } from '@angular/material/tooltip';
import { Routes } from '../../core/models/routes-interfaces/routes';
import { CacheInfo, RouteCacheStats } from '../../core/models/cache-info';
import { AuthService } from '../../core/services/auth.service';
import { CacheService } from '../../core/services/cache.service';
import { LayersService } from '../../core/services/layers.service';
import { SparqiService } from '../../core/services/sparqi.service';
import { saveErrorMessage } from '../../core/http-error-message';
import { HasUnsavedChanges, warnBeforeUnload } from '../../core/guards/unsaved-changes.guard';
import { confirm } from '../../shared/confirm-dialog/confirm-dialog.component';
import { AuditLineComponent } from '../../shared/audit-line/audit-line.component';
import { EndpointPanelComponent } from '../../shared/endpoint-panel/endpoint-panel.component';
import { RouteAccessComponent } from '../../shared/route-access/route-access.component';
import { SparklineComponent } from '../../shared/sparkline/sparkline.component';
import { IfRoleDirective } from '../../shared/if-role.directive';
import { QuickAction } from '../../shared/quick-actions/quick-action';
import { QuickActionsService } from '../../shared/quick-actions/quick-actions.service';
import { effectiveAccess, endpointUrl } from '../../shared/query-endpoint';
import { relativeTime, statusLabel, statusTone } from '../../shared/datasource-health/health-status';
import { breadcrumbLabel, ROUTE_CRUMB } from '../../shared/breadcrumb-label';
import { parseRouteTab, RouteTab, routeLink, routeTabParams } from '../../shared/route-links';
import { RouteDraft } from '../routes/route-draft';
import { RoutesService } from '../routes/routes.service';
import { RoutesLiveService } from '../routes/routes-live.service';
import { RouteActionsService, RouteTarget } from '../routes/route-actions.service';
import { formatErrorRate, formatMs, formatRate, NO_TRAFFIC, routeState, startBlockedReason } from '../routes/routes-derive';
import { cacheOf, CacheInfoResponse } from '../add-route/add-route.component';
import { RouteRequestFieldsComponent } from '../routes/form/route-request-fields.component';
import { RouteDataFieldsComponent } from '../routes/form/route-data-fields.component';
import { RouteCacheFieldsComponent } from '../routes/form/route-cache-fields.component';
import { RouteTemplateEditorComponent } from '../routes/form/route-template-editor.component';
import { RouteTestComponent } from './route-test.component';
import { ConfigRouteService } from './config-route.service';
import { SparqiChatComponent } from './sparqi-chat/sparqi-chat.component';

const CHAT_WIDTH_KEY = 'sparqi-panel-width';
const CHAT_MIN = 300;
const CHAT_MAX = 800;

function storedChatWidth(): number {
  try {
    const width = Number(localStorage.getItem(CHAT_WIDTH_KEY));
    return width >= CHAT_MIN && width <= CHAT_MAX ? width : 420;
  } catch {
    return 420;
  }
}

/**
 * A route's page. A header that stays: its state with start/stop, its endpoint, who may call it, its datasource's
 * health, and its traffic, live. Under it three tabs (in the URL as ?tab=): Overview (request, data, access,
 * caching), Template (the editor) and Test. The tabs edit one draft, saved together from the tab bar, which says
 * when there are unsaved changes; leaving with some asks first. SPARQi opens beside any tab.
 *
 * Its live data comes from the routes page above it (one connection for both).
 */
@Component({
  selector: 'app-config-route',
  templateUrl: './config-route.component.html',
  styleUrls: ['./config-route.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [RouteDraft],
  imports: [RouterLink, MatButton, MatIconButton, MatIcon, MatMenu, MatMenuItem, MatMenuTrigger, MatTooltip,
    AuditLineComponent, EndpointPanelComponent, RouteAccessComponent, SparklineComponent, IfRoleDirective,
    RouteRequestFieldsComponent, RouteDataFieldsComponent, RouteCacheFieldsComponent, RouteTemplateEditorComponent,
    RouteTestComponent, SparqiChatComponent],
})
export class ConfigRouteComponent implements HasUnsavedChanges {
  protected readonly draft = inject(RouteDraft);
  protected readonly live = inject(RoutesLiveService, { optional: true });
  private readonly actions = inject(RouteActionsService);
  private readonly api = inject(RoutesService);
  private readonly configApi = inject(ConfigRouteService);
  private readonly layersApi = inject(LayersService);
  private readonly cache = inject(CacheService);
  private readonly sparqi = inject(SparqiService);
  private readonly quickActions = inject(QuickActionsService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly activated = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly injector = inject(Injector);

  protected readonly routeId = signal('');
  /** The route as stored (null until loaded). */
  protected readonly stored = signal<Routes | null>(null);
  protected readonly loadState = signal<'loading' | 'loaded' | 'missing' | 'failed'>('loading');
  protected readonly loadError = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);
  /** Save was pressed with something missing. */
  protected readonly saveAttempted = signal(false);
  protected readonly pending = signal(false);
  private deleted = false;

  protected readonly tab = signal<RouteTab>('overview');
  /** Tabs shown so far: each is created on first visit, then kept (the editor and the test keep their state). */
  protected readonly visited = signal<ReadonlySet<RouteTab>>(new Set(['overview']));

  protected readonly sparqiEnabled = signal(false);
  protected readonly chatOpen = signal(false);
  protected readonly chatWidth = signal(storedChatWidth());

  protected readonly cacheInfo = signal<CacheInfo | null | undefined>(undefined);
  protected readonly cacheStats = signal<RouteCacheStats | null>(null);
  protected readonly clearingCache = signal(false);

  private readonly test = viewChild(RouteTestComponent);
  private readonly editor = viewChild(RouteTemplateEditorComponent);

  private readonly user = toSignal(inject(AuthService).user$, { initialValue: null });
  protected readonly isEditor = computed(() => (this.user(), this.auth.hasRole('editor')));
  protected readonly now = signal(Date.now());

  get unsavedSubject(): string {
    return this.routeId();
  }

  // --- Live state of the route --------------------------------------------------------------------------------------

  private readonly value = toSignal(this.draft.form.valueChanges.pipe(map(() => this.draft.form.getRawValue())),
    { initialValue: this.draft.form.getRawValue() });

  /** The row the routes list shows for it: live state, datasource health and traffic. */
  protected readonly row = computed(() => this.live?.row(this.routeId()));

  protected readonly started = computed(() => this.row()?.started ?? this.stored()?.status === 'Started');
  protected readonly datasourceId = computed(() => this.stored()?.datasources?.dataSourceId ?? '');
  protected readonly datasourceStatus = computed(() =>
    this.row()?.datasourceStatus ?? (this.stored()?.datasources?.status as never) ?? 'UNKNOWN');
  protected readonly state = computed(() => routeState(this.started(), this.datasourceStatus()));
  protected readonly startBlocked = computed(() =>
    this.started() ? null : startBlockedReason(this.datasourceId(), this.datasourceStatus()));
  protected readonly traffic = computed(() => this.row()?.traffic ?? NO_TRAFFIC);
  /** A route that isn't running and had no calls shows dashes, not zeros (as the list does). */
  protected readonly hasTraffic = computed(() => this.state() === 'running' || this.traffic().calls > 0);
  protected readonly stopNote = computed(() => {
    const s = this.stored();
    if (this.started() || !s) {
      return '';
    }
    const when = s.stoppedAt ? relativeTime(new Date(s.stoppedAt as number).toISOString(), this.now()) : '';
    const why = s.stopReason === 'HEALTH_CHECK' ? 'by the health check' : s.stopReason === 'NO_TEMPLATE' ? 'until it has a template' : '';
    return [why, when].filter(Boolean).join(' · ');
  });

  protected readonly endpoint = computed(() => endpointUrl(this.live?.baseUrl(), this.routeId()));
  protected readonly access = computed(() =>
    effectiveAccess(this.value().accessPolicy, this.live?.serverDefaultAccess() ?? 'open'));
  protected readonly methods = computed(() => this.value().methods);

  constructor() {
    const destroyRef = inject(DestroyRef);

    this.activated.paramMap.pipe(takeUntilDestroyed()).subscribe(params => void this.switchTo(params.get('routeId') ?? ''));
    this.activated.queryParamMap.pipe(takeUntilDestroyed()).subscribe(params => this.showTab(parseRouteTab(params.get('tab'))));

    // The Test tab reads the template's variables the first time it shows
    effect(() => {
      if (this.tab() === 'test') {
        const test = this.test();
        untracked(() => test?.ensureLoaded());
      }
    });

    warnBeforeUnload(() => this.hasUnsavedChanges());
    breadcrumbLabel(ROUTE_CRUMB, () => this.routeId());
    const clock = setInterval(() => this.now.set(Date.now()), 30000);
    destroyRef.onDestroy(() => clearInterval(clock));
    const unregister = this.quickActions.register(this.pageQuickActions());
    destroyRef.onDestroy(unregister);

    this.sparqi.checkHealth().subscribe({
      next: health => this.sparqiEnabled.set(health.status === 'available'),
      error: () => this.sparqiEnabled.set(false),
    });
    this.cache.getCacheInfo().subscribe({
      next: response => {
        this.cacheInfo.set(cacheOf(response as unknown as CacheInfoResponse));
        void this.loadCacheStats();
      },
      error: () => this.cacheInfo.set(null),
    });
  }

  hasUnsavedChanges(): boolean {
    return !this.deleted && this.loadState() === 'loaded' && this.draft.dirty();
  }

  // --- Loading ------------------------------------------------------------------------------------------------------

  /** Opens another route (from the quick actions bar): asks first if this one has unsaved changes. */
  private async switchTo(id: string): Promise<void> {
    const previous = this.routeId();
    if (previous && id !== previous && this.hasUnsavedChanges()) {
      const leave = await firstValueFrom(confirm(this.dialog, {
        title: 'Leave without saving?',
        message: [`Your changes to ${previous} haven't been saved; leaving discards them.`],
        confirmLabel: 'Discard changes',
      }));
      if (!leave) {
        void this.router.navigate(routeLink(previous), { replaceUrl: true, queryParamsHandling: 'preserve' });
        return;
      }
    }
    if (id !== previous) {
      this.routeId.set(id);
      await this.load(id);
    }
  }

  private async load(id: string): Promise<void> {
    this.loadState.set('loading');
    this.loadError.set(null);
    this.saveError.set(null);
    try {
      const [route, template, layers] = await Promise.all([
        firstValueFrom(this.api.getRoute(id)),
        firstValueFrom(this.configApi.getTemplate(id)).catch(() => ''),
        firstValueFrom(this.layersApi.getRouteLayers(id)).catch(() => [] as string[]),
      ]);
      if (this.routeId() !== id) {
        return; // another route was opened meanwhile
      }
      this.stored.set(route);
      this.draft.load(route, template ?? '', (layers ?? []).map(String));
      this.loadState.set('loaded');
      void this.loadCacheStats();
    } catch (error) {
      if (this.routeId() !== id) {
        return;
      }
      const status = (error as { status?: number }).status;
      this.loadState.set(status === 404 ? 'missing' : 'failed');
      this.loadError.set(saveErrorMessage(error));
    }
  }

  protected retry(): void {
    void this.load(this.routeId());
  }

  private async loadCacheStats(): Promise<void> {
    if (!this.cacheInfo() || !this.routeId()) {
      return;
    }
    try {
      this.cacheStats.set(await firstValueFrom(this.cache.getRouteCacheStats(this.routeId())));
    } catch {
      this.cacheStats.set(null);
    }
  }

  // --- Tabs ---------------------------------------------------------------------------------------------------------

  private showTab(tab: RouteTab): void {
    this.tab.set(tab);
    this.visited.update(set => set.has(tab) ? set : new Set([...set, tab]));
  }

  protected selectTab(tab: RouteTab): void {
    // In the URL, so reload and shared links open it; replaced, so Back leaves the page rather than the tab
    void this.router.navigate([], { relativeTo: this.activated, queryParams: routeTabParams(tab), replaceUrl: true });
    this.showTab(tab);
  }

  protected tabKeydown(event: KeyboardEvent): void {
    const tabs: RouteTab[] = ['overview', 'template', 'test'];
    const at = tabs.indexOf(this.tab());
    const next = event.key === 'ArrowRight' ? tabs[(at + 1) % 3] : event.key === 'ArrowLeft' ? tabs[(at + 2) % 3] : null;
    if (next) {
      event.preventDefault();
      this.selectTab(next);
      afterNextRender(() => (document.getElementById(`cr-tab-${next}`) as HTMLElement | null)?.focus(), { injector: this.injector });
    }
  }

  // --- Saving -------------------------------------------------------------------------------------------------------

  protected async save(): Promise<void> {
    this.saveAttempted.set(true);
    this.draft.form.markAllAsTouched();
    if (this.draft.problems().length || this.saving()) {
      if (this.draft.problems().length && this.tab() !== 'overview') {
        this.selectTab('overview');
      }
      return;
    }
    this.saving.set(true);
    this.saveError.set(null);
    try {
      await firstValueFrom(this.api.updateRoute(this.draft.toRequest()), { defaultValue: null });
      this.draft.markSaved();
      this.saveAttempted.set(false);
      this.snackBar.open(`Saved ${this.routeId()}.`, 'Close', { duration: 3500 });
      // The audit line and the stored state come back from the server
      this.stored.set(await firstValueFrom(this.api.getRoute(this.routeId())).catch(() => this.stored()));
      void this.live?.load();
      void this.loadCacheStats();
    } catch (error) {
      this.saveError.set(saveErrorMessage(error));
    } finally {
      this.saving.set(false);
    }
  }

  protected discard(): void {
    this.draft.discard();
    this.saveAttempted.set(false);
    this.saveError.set(null);
  }

  // --- Actions ------------------------------------------------------------------------------------------------------

  private target(): RouteTarget {
    return { id: this.routeId(), startBlocked: this.startBlocked(), perMinute: this.traffic().perMinute };
  }

  protected async start(): Promise<void> {
    this.pending.set(true);
    try {
      const result = await this.actions.start([this.target()]);
      if (result.done.length) {
        this.applyStatus({ status: 'Started', stopReason: null, stoppedAt: null });
      }
    } finally {
      this.pending.set(false);
    }
  }

  protected async stop(): Promise<void> {
    this.pending.set(true);
    try {
      const result = await this.actions.stop([this.target()]);
      if (result?.done.length) {
        this.applyStatus({ status: 'Stopped', stopReason: 'MANUAL', stoppedAt: Date.now() });
      }
    } finally {
      this.pending.set(false);
    }
  }

  private applyStatus(change: Partial<Routes>): void {
    this.stored.update(r => r ? { ...r, ...change } : r);
    this.live?.patch(this.routeId(), change);
    void this.live?.load();
  }

  protected async clone(): Promise<void> {
    const copy = await this.actions.clone(this.routeId());
    if (copy) {
      void this.live?.load();
      void this.router.navigate(routeLink(copy));
    }
  }

  protected async remove(): Promise<void> {
    const result = await this.actions.delete([this.target()]);
    if (result?.done.length) {
      this.deleted = true;
      void this.live?.load();
      void this.router.navigate(['/routes']);
    }
  }

  protected copyUrl(): void {
    this.actions.copy(this.endpoint());
  }

  /** Opens the Test tab and runs the test. */
  protected async runTest(): Promise<void> {
    this.selectTab('test');
    await new Promise<void>(resolve => afterNextRender(() => resolve(), { injector: this.injector }));
    await this.test()?.run();
  }

  protected async clearCache(): Promise<void> {
    const entries = this.cacheStats()?.routeKeyCount;
    const ok = await firstValueFrom(confirm(this.dialog, {
      title: `Clear ${this.routeId()}'s cache?`,
      message: [entries ? `${entries} cached ${entries === 1 ? 'result' : 'results'} will be deleted.`
        : 'Its cached results will be deleted.', 'The next requests go to the datasource until the cache fills again.'],
      confirmLabel: 'Clear cache',
    }));
    if (!ok) {
      return;
    }
    this.clearingCache.set(true);
    try {
      const result = await firstValueFrom(this.cache.clearRouteCache(this.routeId())) as { deletedCount?: number } | null;
      const n = result?.deletedCount ?? 0;
      this.snackBar.open(`Cleared ${n} cached ${n === 1 ? 'result' : 'results'}.`, 'Close', { duration: 4000 });
      await this.loadCacheStats();
    } catch (error) {
      this.snackBar.open(`Cache not cleared: ${saveErrorMessage(error)}`, 'Close', { duration: 10000, panelClass: ['error-snackbar'] });
    } finally {
      this.clearingCache.set(false);
    }
  }

  // --- SPARQi panel -------------------------------------------------------------------------------------------------

  protected toggleChat(): void {
    this.chatOpen.update(open => !open);
    this.relayoutEditor();
  }

  protected startResize(event: PointerEvent): void {
    event.preventDefault();
    const startX = event.clientX;
    const startWidth = this.chatWidth();
    const move = (e: PointerEvent) => {
      this.chatWidth.set(Math.min(CHAT_MAX, Math.max(CHAT_MIN, startWidth + startX - e.clientX)));
    };
    const up = () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
      try {
        localStorage.setItem(CHAT_WIDTH_KEY, String(this.chatWidth()));
      } catch {
        // not kept
      }
      this.relayoutEditor();
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
  }

  protected resizeKey(event: KeyboardEvent): void {
    const step = event.key === 'ArrowLeft' ? 24 : event.key === 'ArrowRight' ? -24 : 0;
    if (step) {
      event.preventDefault();
      this.chatWidth.update(w => Math.min(CHAT_MAX, Math.max(CHAT_MIN, w + step)));
      this.relayoutEditor();
    }
  }

  private relayoutEditor(): void {
    setTimeout(() => this.editor()?.monacoEditor?.layout(), 320);
  }

  // --- Display ------------------------------------------------------------------------------------------------------

  protected readonly formatRate = formatRate;
  protected readonly formatErrorRate = formatErrorRate;
  protected readonly formatMs = formatMs;
  protected readonly statusLabel = statusLabel;
  protected readonly statusTone = statusTone;

  protected readonly stateLabel = computed(() => {
    switch (this.state()) {
      case 'running': return 'Running';
      case 'blocked': return this.datasourceStatus() === 'DOWN' ? 'Blocked: datasource down' : 'Blocked: datasource disabled';
      default: return 'Stopped';
    }
  });

  protected readonly changedText = computed(() => {
    const names: Record<string, string> = {
      methods: 'methods', description: 'description', datasourceId: 'datasource', graphmart: 'graphmart',
      layers: 'layers', template: 'template', cacheEnabled: 'caching', cacheTtlSeconds: 'caching',
      cacheKeyStrategy: 'caching', accessPolicy: 'access', requiredRoles: 'access', routeId: 'name',
    };
    return [...new Set(this.draft.changedFields().map(f => names[f] ?? f))].join(', ');
  });

  protected readonly hitRate = computed(() => {
    const s = this.cacheStats()?.globalStats;
    const total = (s?.hits ?? 0) + (s?.misses ?? 0);
    return total ? `${Math.round((s!.hits / total) * 100)}%` : '';
  });

  // --- Quick actions ------------------------------------------------------------------------------------------------

  /** What this page adds to the quick actions bar while it is open. Roles as on the page's own buttons. */
  private pageQuickActions(): QuickAction[] {
    return [
      {
        id: 'route:save', label: 'Save route', icon: 'save', group: 'page', minRole: 'editor', keywords: ['update'],
        disabledReason: () => !this.draft.dirty() ? 'Nothing to save' : this.draft.problems()[0] ?? null,
        run: () => this.save(),
      },
      {
        id: 'route:start', label: 'Start route', icon: 'play_arrow', group: 'page', minRole: 'editor',
        keywords: ['turn on', 'enable'],
        visible: () => this.loadState() === 'loaded' && !this.started(),
        disabledReason: () => this.startBlocked(),
        run: () => this.start(),
      },
      {
        id: 'route:stop', label: 'Stop route', icon: 'stop', group: 'page', minRole: 'editor',
        keywords: ['turn off', 'disable'],
        visible: () => this.loadState() === 'loaded' && this.started(),
        run: () => this.stop(),
      },
      {
        id: 'route:test', label: 'Run test request', icon: 'science', group: 'page', minRole: 'editor',
        keywords: ['execute', 'try'],
        run: () => this.runTest(),
      },
      {
        id: 'route:template', label: 'Edit template', icon: 'code', group: 'page', keywords: ['freemarker', 'sparql'],
        run: () => this.selectTab('template'),
      },
      {
        id: 'route:clear-cache', label: "Clear this route's cache", icon: 'delete_sweep', group: 'page',
        minRole: 'editor', keywords: ['flush'],
        disabledReason: () => !this.cacheInfo() ? 'This server has no query cache'
          : this.clearingCache() ? 'Already clearing' : null,
        run: () => this.clearCache(),
      },
      {
        id: 'route:sparqi', label: 'Toggle SPARQi assistant', icon: 'smart_toy', group: 'page', minRole: 'editor',
        keywords: ['ai', 'chat', 'assistant'],
        visible: () => this.sparqiEnabled(),
        run: () => this.toggleChat(),
      },
      {
        id: 'route:clone', label: 'Clone route', icon: 'content_copy', group: 'page', minRole: 'editor', keywords: ['copy', 'duplicate'],
        run: () => this.clone(),
      },
      {
        id: 'route:copy-url', label: 'Copy endpoint URL', icon: 'link', group: 'page', keywords: ['link', 'url', 'curl'],
        run: () => this.copyUrl(),
      },
    ];
  }
}
