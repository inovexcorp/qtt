import { routeLink } from '../../shared/route-links';
import { breadcrumbLabel, DATASOURCE_CRUMB } from '../../shared/breadcrumb-label';
import { afterNextRender, ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, signal, untracked,
  viewChild } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { interval, map } from 'rxjs';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatAnchor, MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { MatFormField, MatError, MatHint, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatCell, MatCellDef, MatColumnDef, MatHeaderCell, MatHeaderCellDef, MatHeaderRow, MatHeaderRowDef, MatNoDataRow,
  MatRow, MatRowDef, MatTable, MatTableDataSource } from '@angular/material/table';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort, MatSortHeader } from '@angular/material/sort';
import { DatasourceService } from '../../core/services/datasource.service';
import { DatasourceLiveService } from '../../core/services/datasource-live.service';
import { METRICS_HISTORY, MetricsLiveService } from '../../core/services/metrics-live.service';
import { AuthService } from '../../core/services/auth.service';
import { Datasources } from '../../core/models/datasources';
import { saveErrorMessage } from '../../core/http-error-message';
import { DatasourceStatus } from '../../core/models/datasource-health';
import { AuditLineComponent } from '../../shared/audit-line/audit-line.component';
import { IfRoleDirective } from '../../shared/if-role.directive';
import { TweenNumberDirective } from '../../shared/tween-number.directive';
import { HealthPillComponent } from '../../shared/datasource-health/health-pill.component';
import { CheckHistoryComponent } from '../../shared/datasource-health/check-history.component';
import { relativeTime } from '../../shared/datasource-health/health-status';
import { LiveIndicatorComponent } from '../../shared/live-indicator/live-indicator.component';
import { liveAnnouncement, liveText } from '../datasources/datasources.component';
import { RouteRow, summarize, toRow } from '../metrics/metrics-derive';
import { DeleteDatasourceModalComponent } from '../delete-datasource-modal/delete-datasource-modal.component';
import { ConfigDatasourceModalComponent, DatasourceChange, SaveDatasourceDialogData } from '../config-datasource-modal/config-datasource-modal.component';
import { QuickActionsService } from '../../shared/quick-actions/quick-actions.service';
import { QuickAction } from '../../shared/quick-actions/quick-action';
import { confirm } from '../../shared/confirm-dialog/confirm-dialog.component';

/** The form's fields, labelled for the confirmation's list of changes. */
const FIELD_LABELS: Record<string, string> = {
  url: 'URL', username: 'Username', password: 'Password', timeOutSeconds: 'Timeout',
  maxQueryHeaderLength: 'Max query header length', validateCertificate: 'HTTPS certificates',
};

const WHOLE_NUMBER = Validators.pattern(/^\d+$/);

/**
 * One datasource: its live health (status, the last checks, what failed), how QTT connects to it (edit, test,
 * save), and the routes that query it with their live totals. Health comes from the datasources page's
 * {@link DatasourceLiveService} (pushed by the server); usage from a {@link MetricsLiveService} scoped to it.
 */
@Component({
  selector: 'app-config-datasource',
  templateUrl: './config-datasource.component.html',
  styleUrls: ['./config-datasource.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [MetricsLiveService, { provide: METRICS_HISTORY, useValue: false }],
  imports: [ReactiveFormsModule, RouterLink, DecimalPipe, MatAnchor, MatButton, MatIconButton, MatIcon, MatTooltip, MatFormField,
    MatLabel, MatInput, MatSuffix, MatError, MatHint, MatSlideToggle, MatProgressSpinner, MatTable, MatSort,
    MatColumnDef, MatHeaderCellDef, MatHeaderCell, MatSortHeader, MatCellDef, MatCell, MatHeaderRowDef, MatHeaderRow,
    MatRowDef, MatRow, MatNoDataRow, MatPaginator, AuditLineComponent, IfRoleDirective, TweenNumberDirective,
    HealthPillComponent, CheckHistoryComponent, LiveIndicatorComponent],
})
export class ConfigDatasourceComponent {
  protected readonly live = inject(DatasourceLiveService);
  protected readonly usage = inject(MetricsLiveService);
  private readonly api = inject(DatasourceService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly dataSourceId = toSignal(inject(ActivatedRoute).paramMap.pipe(map(p => p.get('dataSourceId') ?? '')),
    { initialValue: '' });

  /** The stored settings (the API never returns the password); null while loading. */
  protected readonly datasource = signal<Datasources | null>(null);
  protected readonly notFound = signal(false);
  protected readonly loadFailed = signal(false);

  // The toolbar breadcrumb shows the datasource's name
  private readonly crumb = breadcrumbLabel(DATASOURCE_CRUMB, () => this.dataSourceId());
  protected readonly health = computed(() => this.live.datasources()?.find(d => d.dataSourceId === this.dataSourceId()));
  protected readonly status = computed<DatasourceStatus>(() =>
    this.health()?.status ?? (this.datasource()?.status as DatasourceStatus) ?? 'UNKNOWN');

  protected readonly now = toSignal(interval(15000).pipe(map(() => Date.now())), { initialValue: Date.now() });
  protected readonly ready = signal(false);
  protected readonly isAdmin = toSignal(this.auth.user$.pipe(map(() => this.auth.hasRole('admin'))),
    { initialValue: this.auth.hasRole('admin') });

  // --- Health ------------------------------------------------------------------------------------------------------

  /** The action under way, for its button's progress. */
  protected readonly busy = signal<'check' | 'enable' | 'disable' | null>(null);

  protected readonly statement = computed(() => {
    const health = this.health();
    const failures = health?.consecutiveFailures ?? 0;
    const checks = health?.recentChecks ?? [];
    switch (this.status()) {
      case 'UP': {
        const last = checks.at(-1);
        const streak = trailing(checks, 'UP');
        return {
          title: last?.status === 'UP' && last.responseTimeMs != null ? `Responding in ${last.responseTimeMs} ms` : 'Responding',
          detail: streak > 1 ? `Up for the last ${streak} checks` : 'Answering health checks',
        };
      }
      case 'DOWN': {
        // When the current run of failures began (as far back as the history goes).
        const run = trailing(checks, 'DOWN');
        const since = run ? relativeTime(checks[checks.length - run].checkTime, this.now()) : '';
        return {
          title: !since || since === 'just now' ? 'Failing' : since.endsWith(' ago') ? `Failing for ${since.slice(0, -4)}` : `Failing since ${since}`,
          detail: failures > 1 ? `${failures} failed checks in a row` : 'The last check failed',
        };
      }
      case 'CHECKING':
        return { title: 'Checking now…', detail: 'Asking the server for its graphmarts' };
      case 'DISABLED':
        return { title: 'Disabled', detail: 'Health checks don\'t run while it is disabled' };
      default:
        return { title: 'Not checked yet', detail: this.live.healthCheckEnabled() ? 'Waiting for the next scheduled check' : 'Run a check to see its status' };
    }
  });

  protected readonly lastChecked = computed(() =>
    relativeTime(this.health()?.lastHealthCheck ?? null, this.now()));

  protected readonly liveText = computed(() => liveText(this.live.status(), this.live.transport(), this.live.failed(),
    this.live.updatedAt(), this.now()));
  protected readonly liveAnnouncement = computed(() => liveAnnouncement(this.live.status()));

  // --- Usage -------------------------------------------------------------------------------------------------------

  protected readonly summary = computed(() => {
    const routes = this.usage.routes();
    return routes ? summarize(routes) : null;
  });
  protected readonly stopped = computed(() => (this.usage.routes() ?? []).filter(r => r.state !== 'Started').length);
  protected readonly failedTotal = computed(() => (this.usage.routes() ?? []).reduce((sum, r) => sum + r.exchangesFailed, 0));
  protected readonly avgParts = computed(() => {
    const ms = this.summary()?.avgProcessingMs;
    return ms == null ? null : ms >= 1000 ? { value: ms / 1000, unit: 's' } : { value: ms, unit: 'ms' };
  });
  protected readonly missingReason = computed(() => this.usage.routesFailed() ? 'Unavailable'
    : this.summary()?.endpoints ? 'No exchanges yet' : 'No routes use it');

  protected readonly columns = ['route', 'state', 'completed', 'failed', 'successRate', 'mean', 'range', 'uptime'];
  protected readonly table = new MatTableDataSource<RouteRow>([]);
  protected readonly tableMode = computed<'loading' | 'error' | 'empty' | null>(() => {
    const routes = this.usage.routes();
    if (routes === null) {
      return this.usage.routesFailed() ? 'error' : 'loading';
    }
    return routes.length ? null : 'empty';
  });
  private readonly sort = viewChild(MatSort);
  private readonly paginator = viewChild(MatPaginator);

  // --- Connection form ---------------------------------------------------------------------------------------------

  protected readonly form = new FormGroup({
    url: new FormControl('', { nonNullable: true, validators: Validators.required }),
    username: new FormControl('', { nonNullable: true, validators: Validators.required }),
    // Blank keeps the stored password, unless the URL or username changes
    password: new FormControl('', {
      nonNullable: true,
      validators: (control: AbstractControl): ValidationErrors | null =>
        this.passwordRequiredForChange(control) ? { requiredForChange: true } : null,
    }),
    timeOutSeconds: new FormControl('', { nonNullable: true, validators: [Validators.required, WHOLE_NUMBER] }),
    maxQueryHeaderLength: new FormControl('', { nonNullable: true, validators: [Validators.required, WHOLE_NUMBER] }),
    validateCertificate: new FormControl(true, { nonNullable: true }),
  });
  protected readonly hidePassword = signal(true);
  /** Re-evaluated on every form change (the form isn't a signal). */
  private readonly formTick = toSignal(this.form.valueChanges.pipe(map(() => Date.now())), { initialValue: 0 });
  protected readonly changes = computed(() => {
    this.formTick();
    return this.diff();
  });

  protected readonly test = signal<{ state: 'idle' | 'testing' | 'success' | 'error'; message?: string }>({ state: 'idle' });

  constructor() {
    this.table.filterPredicate = (row, term) => row.route.toLowerCase().includes(term);
    this.table.sortingDataAccessor = (row, column) => {
      const value = row[column === 'range' ? 'max' : column as keyof RouteRow];
      return value === null ? -1 : value;
    };
    effect(() => {
      this.table.data = (this.usage.routes() ?? []).map(toRow);
    });
    effect(() => {
      this.table.sort = this.sort() ?? null;
      this.table.paginator = this.paginator() ?? null;
    });

    // A new datasource id (also the first): load it, scope the usage to it, start the form over.
    effect(() => {
      const id = this.dataSourceId();
      if (id) {
        untracked(() => {
          this.usage.setDatasource(id);
          this.test.set({ state: 'idle' });
          this.load(id);
        });
      }
    });

    effect(() => {
      if (this.isAdmin()) {
        this.form.enable({ emitEvent: false });
      } else {
        this.form.disable({ emitEvent: false });
      }
    });

    this.form.controls.url.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.revalidatePassword());
    this.form.controls.username.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.revalidatePassword());
    // A test result describes the settings it tested; editing them makes it stale.
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      if (this.test().state !== 'testing' && this.test().state !== 'idle') {
        this.test.set({ state: 'idle' });
      }
    });

    afterNextRender(() => this.ready.set(true));

    const unregister = inject(QuickActionsService).register(this.pageQuickActions());
    this.destroyRef.onDestroy(unregister);
  }

  // --- Quick actions bar -------------------------------------------------------------------------------------------

  /** What this page adds to the quick actions bar while it is open. Roles and states as on the page's buttons. */
  private pageQuickActions(): QuickAction[] {
    const formBlocks = () => this.datasource() === null ? 'Still loading'
      : this.form.invalid ? 'Some connection settings are invalid' : null;
    return [
      {
        id: 'datasource:save', label: 'Save datasource', icon: 'check', group: 'page', minRole: 'admin',
        keywords: ['submit', 'update', 'save changes'],
        disabledReason: () => formBlocks() ?? (this.changes().length === 0 ? 'Nothing has changed' : null),
        run: () => this.save()
      },
      {
        id: 'datasource:test', label: 'Test connection', icon: 'network_check', group: 'page', minRole: 'admin',
        keywords: ['try', 'ping'],
        disabledReason: () => formBlocks() ?? (this.test().state === 'testing' ? 'A test is already running' : null),
        run: () => {
          this.scrollTo('connection-title');
          this.runTest();
        }
      },
      {
        id: 'datasource:health', label: 'Run health check', icon: 'monitor_heart', group: 'page', minRole: 'editor',
        keywords: ['check now', 'status'],
        disabledReason: () => !this.live.healthCheckEnabled() ? 'Automated health checks are off on the server'
          : this.status() === 'DISABLED' ? 'Enable the datasource to check it'
          : this.busy() !== null || this.status() === 'CHECKING' ? 'A check is already running' : null,
        run: () => this.checkNow()
      },
      {
        id: 'datasource:enable', label: 'Enable datasource', icon: 'play_arrow', group: 'page', minRole: 'admin',
        keywords: ['turn on'],
        visible: () => this.status() === 'DISABLED',
        disabledReason: () => this.busy() !== null ? 'Busy with another action' : null,
        run: () => this.setEnabled(true)
      },
      {
        id: 'datasource:disable', label: 'Disable datasource', icon: 'block', group: 'page', minRole: 'admin',
        keywords: ['turn off'],
        visible: () => this.status() !== 'DISABLED',
        disabledReason: () => this.busy() !== null ? 'Busy with another action' : null,
        run: () => this.confirmDisable()
      },
      {
        id: 'datasource:delete', label: 'Delete datasource', icon: 'delete', group: 'page', minRole: 'admin',
        keywords: ['remove'],
        run: () => this.openDelete()
      },
      {
        id: 'datasource:usage', label: 'Show usage', icon: 'insights', group: 'page',
        keywords: ['metrics', 'routes using'],
        run: () => this.scrollTo('usage-title')
      }
    ];
  }

  /**
   * Disabling from the quick actions bar asks first: a keyboard-run action is easy to trigger by mistake, and the
   * routes using the datasource stop answering queries.
   */
  private confirmDisable(): void {
    const id = this.dataSourceId();
    const routes = this.datasource()?.camelRouteTemplateNames?.length ?? 0;
    confirm(this.dialog, {
      title: `Disable ${id}?`,
      message: [
        routes === 0 ? 'No routes use this datasource.'
          : `${routes === 1 ? 'The route' : `All ${routes} routes`} using it stop answering queries until it is enabled again.`,
        'Health checks also stop while it is disabled.'
      ],
      confirmLabel: 'Disable'
    }).subscribe(confirmed => {
      if (confirmed) {
        this.setEnabled(false);
      }
    });
  }

  private scrollTo(headingId: string): void {
    document.getElementById(headingId)?.closest('section')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  // --- Loading -----------------------------------------------------------------------------------------------------

  private load(id: string): void {
    this.api.getDatasource(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: datasource => {
        this.datasource.set(datasource);
        this.notFound.set(false);
        this.loadFailed.set(false);
        this.resetForm(datasource);
      },
      error: error => {
        console.error('Failed to load datasource:', error);
        this.notFound.set(error?.status === 404);
        this.loadFailed.set(error?.status !== 404);
      },
    });
  }

  private resetForm(datasource: Datasources): void {
    this.form.reset({
      url: datasource.url,
      username: datasource.username,
      password: '',
      timeOutSeconds: String(datasource.timeOutSeconds ?? ''),
      maxQueryHeaderLength: String(datasource.maxQueryHeaderLength ?? ''),
      validateCertificate: datasource.validateCertificate !== false,
    });
  }

  protected retryLoad(): void {
    this.load(this.dataSourceId());
  }

  // --- Health actions ----------------------------------------------------------------------------------------------

  protected checkNow(): void {
    const id = this.dataSourceId();
    const before = this.status();
    this.busy.set('check');
    this.live.patch(id, { status: 'CHECKING' });
    this.api.triggerHealthCheck(id).subscribe({
      next: result => {
        this.busy.set(null);
        // Shown at once; the pushed snapshot that follows brings the new check into the history.
        if (result?.status) {
          this.live.patch(id, { status: result.status, lastHealthError: result.lastHealthError ?? null });
        }
      },
      error: error => {
        this.busy.set(null);
        this.live.patch(id, { status: before });
        this.snackBar.open(`Couldn't run the check: ${saveErrorMessage(error)}`, 'Dismiss', { duration: 6000, panelClass: 'error-snackbar' });
      },
    });
  }

  protected setEnabled(enabled: boolean): void {
    const id = this.dataSourceId();
    const before = this.status();
    this.busy.set(enabled ? 'enable' : 'disable');
    this.live.patch(id, { status: enabled ? 'UNKNOWN' : 'DISABLED' });
    (enabled ? this.api.enableDatasource(id) : this.api.disableDatasource(id)).subscribe({
      next: () => {
        this.busy.set(null);
        this.snackBar.open(enabled ? `Enabled ${id}; health checks resume` : `Disabled ${id}; health checks stop`,
          'Dismiss', { duration: 4000 });
      },
      error: error => {
        this.busy.set(null);
        this.live.patch(id, { status: before });
        this.snackBar.open(`Couldn't ${enabled ? 'enable' : 'disable'} ${id}: ${saveErrorMessage(error)}`, 'Dismiss',
          { duration: 6000, panelClass: 'error-snackbar' });
      },
    });
  }

  protected openDelete(): void {
    this.dialog.open(DeleteDatasourceModalComponent, {
      data: { dataSourceId: this.dataSourceId(), associatedRoutes: this.datasource()?.camelRouteTemplateNames ?? [] },
      panelClass: 'qtt-dialog-panel',
      autoFocus: 'dialog',
    });
  }

  // --- Usage -------------------------------------------------------------------------------------------------------

  protected search(term: string): void {
    this.table.filter = term.trim().toLowerCase();
    this.table.paginator?.firstPage();
  }

  protected togglePause(): void {
    if (this.live.status() === 'paused') {
      this.live.resume();
      this.usage.resume();
    } else {
      this.live.pause();
      this.usage.pause();
    }
  }

  protected refreshAll(): void {
    this.live.refresh();
    this.usage.refresh();
  }

  // --- Connection --------------------------------------------------------------------------------------------------

  protected runTest(): void {
    this.test.set({ state: 'testing' });
    this.api.testDatasource(this.payload()).subscribe({
      next: response => this.test.set(response?.status === 'success'
        ? { state: 'success' }
        : { state: 'error', message: response?.message || 'The server gave no reason.' }),
      error: error => this.test.set({ state: 'error', message: saveErrorMessage(error) }),
    });
  }

  protected discard(): void {
    const datasource = this.datasource();
    if (datasource) {
      this.resetForm(datasource);
    }
  }

  protected save(): void {
    if (this.form.invalid || this.changes().length === 0) {
      this.form.markAllAsTouched();
      return;
    }
    const data: SaveDatasourceDialogData = {
      datasource: this.payload(),
      changes: this.changes(),
      associatedRoutes: this.datasource()?.camelRouteTemplateNames ?? [],
    };
    this.dialog.open(ConfigDatasourceModalComponent, { data, panelClass: 'qtt-dialog-panel', autoFocus: 'dialog', width: '34rem' })
      .afterClosed().subscribe(saved => {
        if (saved) {
          this.snackBar.open(`Saved changes to ${data.datasource.dataSourceId}`, 'Dismiss', { duration: 4000 });
          this.load(this.dataSourceId());
        }
      });
  }

  private payload(): Datasources {
    const value = this.form.getRawValue();
    return {
      dataSourceId: this.dataSourceId(),
      url: value.url.trim(),
      username: value.username,
      password: value.password || undefined,
      timeOutSeconds: value.timeOutSeconds,
      maxQueryHeaderLength: value.maxQueryHeaderLength,
      validateCertificate: value.validateCertificate,
    } as Datasources;
  }

  /** What the form changes, for the confirmation. */
  private diff(): DatasourceChange[] {
    const stored = this.datasource();
    if (!stored) {
      return [];
    }
    const value = this.form.getRawValue();
    const cert = (v: boolean) => v ? 'Validated' : 'Not validated';
    const pairs: [keyof typeof value, string, string][] = [
      ['url', stored.url, value.url.trim()],
      ['username', stored.username, value.username],
      ['timeOutSeconds', String(stored.timeOutSeconds), value.timeOutSeconds],
      ['maxQueryHeaderLength', String(stored.maxQueryHeaderLength), value.maxQueryHeaderLength],
      ['validateCertificate', cert(stored.validateCertificate !== false), cert(value.validateCertificate)],
    ];
    const changes: DatasourceChange[] = pairs.filter(([, from, to]) => from !== to)
      .map(([field, from, to]) => ({ label: FIELD_LABELS[field], from, to }));
    if (value.password) {
      changes.push({ label: FIELD_LABELS['password'], from: null, to: null });
    }
    return changes;
  }

  /** True when the URL or username differs from the stored datasource and no new password was entered. */
  private passwordRequiredForChange(password: AbstractControl): boolean {
    const group = password.parent;
    const stored = this.datasource?.();
    if (!group || password.value || !stored) {
      return false;
    }
    return group.get('url')?.value.trim() !== stored.url || group.get('username')?.value !== stored.username;
  }

  private revalidatePassword(): void {
    const password = this.form.controls.password;
    password.updateValueAndValidity({ emitEvent: false });
    if (password.invalid) {
      password.markAsTouched();
    }
  }

  // Fixed precision, so a figure keeps its width while it counts to a new value (as on the metrics page).
  protected readonly formatOneDecimal = (v: number) =>
    v.toLocaleString(undefined, v >= 100 ? { maximumFractionDigits: 0 } : { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  protected readonly formatPercent = (v: number) => v === 100 ? '100'
    : Math.min(v, 99.9).toLocaleString(undefined, { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  protected readonly formatInteger = (v: number) => Math.round(v).toLocaleString();

  protected routeLink(route: string): string[] {
    return routeLink(route);
  }

  protected backToList(): void {
    void this.router.navigate(['/datasources']);
  }
}

/** How many of the most recent checks in a row have this status. */
function trailing(checks: { status: string }[], status: string): number {
  let n = 0;
  for (let i = checks.length - 1; i >= 0 && checks[i].status === status; i--) {
    n++;
  }
  return n;
}
