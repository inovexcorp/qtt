import { Component, ElementRef, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { Subscription } from 'rxjs';
import { SettingsService, SsoInfo } from '../../core/services/settings.service';
import { AuthService } from '../../core/services/auth.service';
import { CacheService } from '../../core/services/cache.service';
import { SparqiService } from '../../core/services/sparqi.service';
import { CacheStatistics } from '../../core/models/ontology/cache-statistics';
import { CacheInfo, CacheStats } from '../../core/models/cache-info';
import { SparqiMetricsSummary } from '../../core/models/sparqi.models';
import { SparqiMetricsModalComponent } from './sparqi-metrics-modal/sparqi-metrics-modal.component';

/** The sections of the Settings page; the current one is in the URL (?section=…). */
export type SettingsSection = 'overview' | 'sign-in' | 'access' | 'caching' | 'sparqi';

/** How a subsystem is doing, for the status list on the overview and the warnings in the section nav. */
export type StatusTone = 'ok' | 'warn' | 'off' | 'pending';

export interface StatusLine {
  label: string;
  tone: StatusTone;
  text: string;
  section: SettingsSection;
}

const SECTIONS: { id: SettingsSection; label: string; icon: string }[] = [
  { id: 'overview', label: 'Overview', icon: 'space_dashboard' },
  { id: 'sign-in', label: 'Sign-in', icon: 'lock' },
  { id: 'access', label: 'Query access', icon: 'vpn_key' },
  { id: 'caching', label: 'Caching', icon: 'cached' },
  { id: 'sparqi', label: 'SPARQi', icon: 'smart_toy' }
];

@Component({
  selector: 'app-settings',
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.scss']
})
export class SettingsComponent implements OnInit, OnDestroy {

  readonly sections = SECTIONS;
  section: SettingsSection = 'overview';

  databaseType: string | undefined;
  databaseRemoteUrl: string | undefined;
  uptime: string | undefined;
  version: string | undefined;
  datasourceCount: number | undefined;
  routeCount: number | undefined;
  cacheStats: CacheStatistics | undefined;

  // Single sign-on configuration
  ssoInfo: SsoInfo | undefined;
  ssoLoadFailed = false;

  // Redis cache properties
  redisCacheInfo: CacheInfo | undefined;
  redisCacheStats: CacheStats | undefined;
  redisCacheAvailable: boolean = false;
  redisCacheChecked = false;

  // SPARQi metrics properties
  sparqiMetricsSummary: SparqiMetricsSummary | undefined;
  sparqiEnabled: boolean = false;
  sparqiActiveSessions: number = 0;
  sparqiHealthChecked: boolean = false;

  private sectionSub?: Subscription;

  constructor(
    private settingsService: SettingsService,
    private cacheService: CacheService,
    private sparqiService: SparqiService,
    private dialog: MatDialog,
    private route: ActivatedRoute,
    private host: ElementRef<HTMLElement>,
    public auth: AuthService
  ) { }

  ngOnInit(): void {
    this.sectionSub = this.route.queryParamMap.subscribe(params => {
      const wanted = params.get('section');
      this.section = SECTIONS.some(s => s.id === wanted) ? wanted as SettingsSection : 'overview';
      // On narrow screens the section list scrolls sideways: keep the current one in view.
      setTimeout(() => this.host.nativeElement.querySelector('.section-link.active')
        ?.scrollIntoView?.({ block: 'nearest', inline: 'nearest' }));
    });

    this.settingsService.getDatabaseInfo().subscribe(info => {
      this.databaseType = info.datasourceType;
      this.databaseRemoteUrl = info.datasourceUrl;
      this.uptime = info.uptime;
      this.version = info.version;
    });

    this.settingsService.getStats().subscribe(stats => {
      this.datasourceCount = stats.datasources;
      this.routeCount = stats.routes;
    });

    this.settingsService.getSsoInfo().subscribe({
      next: info => this.ssoInfo = info,
      error: () => this.ssoLoadFailed = true
    });

    this.settingsService.getCacheStatistics().subscribe(stats => {
      this.cacheStats = stats;
    });

    // Load Redis cache information and statistics
    this.cacheService.getCacheInfo().subscribe({
      next: (response: any) => {
        // Extract nested info and stats objects from the response
        this.redisCacheInfo = response.info;
        this.redisCacheStats = response.stats;
        this.redisCacheAvailable = response.available;
        this.redisCacheChecked = true;
      },
      error: (error) => {
        console.error('Failed to load Redis cache info:', error);
        this.redisCacheAvailable = false;
        this.redisCacheChecked = true;
      }
    });

    // Load SPARQi metrics
    this.loadSparqiMetrics();
  }

  ngOnDestroy(): void {
    this.sectionSub?.unsubscribe();
  }

  /** Query params for a section link; the overview keeps the plain /settings URL. */
  sectionParams(id: SettingsSection): { section: string | null } {
    return { section: id === 'overview' ? null : id };
  }

  loadSparqiMetrics(): void {
    // First check if SPARQi is enabled
    this.sparqiService.checkHealth().subscribe({
      next: (health) => {
        this.sparqiEnabled = health.status === 'available';
        this.sparqiActiveSessions = health.activeSessions;
        this.sparqiHealthChecked = true;

        // Only load metrics if SPARQi is enabled
        if (this.sparqiEnabled) {
          this.sparqiService.getMetricsSummary().subscribe({
            next: (summary) => {
              this.sparqiMetricsSummary = summary;
            },
            error: (error) => {
              console.error('Failed to load SPARQi metrics summary:', error);
            }
          });
        }
      },
      error: (error) => {
        console.error('Failed to check SPARQi health:', error);
        this.sparqiHealthChecked = true;
        this.sparqiEnabled = false;
      }
    });
  }

  openMetricsCharts(): void {
    this.dialog.open(SparqiMetricsModalComponent, {
      width: '1400px',
      maxWidth: '95vw',
      maxHeight: '90vh',
      panelClass: 'sparqi-metrics-dialog-panel',
      autoFocus: false
    });
  }

  // ---- Status at a glance -------------------------------------------------------------------------------------------

  get signInStatus(): StatusLine {
    const line = (tone: StatusTone, text: string): StatusLine => ({ label: 'Sign-in', tone, text, section: 'sign-in' });
    if (this.ssoLoadFailed) return line('off', 'Could not load the sign-in configuration');
    if (!this.ssoInfo) return line('pending', 'Checking…');
    if (!this.ssoInfo.enabled) return line('warn', 'Off: anyone who can reach this server can change it');
    return line('ok', 'On' + (this.ssoInfo.issuer ? ' · ' + this.hostOf(this.ssoInfo.issuer) : ''));
  }

  get queryAccessStatus(): StatusLine {
    const line = (tone: StatusTone, text: string): StatusLine => ({ label: 'Query access', tone, text, section: 'access' });
    if (this.ssoLoadFailed) return line('off', 'Could not load the access settings');
    if (!this.ssoInfo) return line('pending', 'Checking…');
    const total = this.ssoInfo.queryRoutes;
    const open = this.ssoInfo.openQueryRoutes;
    if (total === undefined || open === undefined) {
      return this.ssoInfo.queryAuthDefault === 'authenticated'
        ? line('ok', 'Routes require a token or an API key by default')
        : line('warn', 'Routes are open to anyone by default');
    }
    if (total === 0) return line('ok', 'No query routes yet');
    if (open === 0) return line('ok', `All ${total} routes require a token or an API key`);
    return line('warn', `${open} of ${total} routes are open to anyone`);
  }

  get queryCacheStatus(): StatusLine {
    const line = (tone: StatusTone, text: string): StatusLine => ({ label: 'Query cache', tone, text, section: 'caching' });
    if (!this.redisCacheChecked) return line('pending', 'Checking…');
    if (!this.isCacheEnabled()) return line('off', 'Off');
    if (!this.redisCacheInfo?.connected) return line('warn', 'Disconnected from Redis');
    return line('ok', `Connected · ${this.formatCacheHitRate(this.redisCacheStats)} hit rate`);
  }

  get ontologyCacheStatus(): StatusLine {
    const line = (tone: StatusTone, text: string): StatusLine => ({ label: 'Ontology cache', tone, text, section: 'caching' });
    if (!this.cacheStats) return line('pending', 'Checking…');
    return line('ok', `${this.formatNumber(this.cacheStats.size)} entries · ${this.formatHitRate(this.cacheStats.hitRate)} hit rate`);
  }

  get sparqiStatus(): StatusLine {
    const line = (tone: StatusTone, text: string): StatusLine => ({ label: 'SPARQi', tone, text, section: 'sparqi' });
    if (!this.sparqiHealthChecked) return line('pending', 'Checking…');
    if (!this.sparqiEnabled) return line('off', 'Off');
    const s = this.sparqiMetricsSummary;
    return line('ok', s
      ? `On · ${this.formatNumber(s.totalSessions)} sessions, ${this.formatCost(s.totalEstimatedCost)} estimated cost`
      : 'On');
  }

  get statusLines(): StatusLine[] {
    return [this.signInStatus, this.queryAccessStatus, this.queryCacheStatus, this.ontologyCacheStatus, this.sparqiStatus];
  }

  /** True when the section holds something an operator should look at (shown as a warning in the section nav). */
  needsAttention(id: SettingsSection): boolean {
    return this.statusLines.some(s => s.section === id && s.tone === 'warn');
  }

  statusIcon(tone: StatusTone): string {
    switch (tone) {
      case 'ok': return 'check_circle';
      case 'warn': return 'warning';
      case 'pending': return 'more_horiz';
      default: return 'remove_circle_outline';
    }
  }

  // ---- Formatting ---------------------------------------------------------------------------------------------------

  /** Group names mapped to a role, or a note that the role is only granted directly. */
  formatGroups(groups: string[] | undefined): string {
    return groups && groups.length ? groups.join(', ') : 'None (role assigned directly in the IdP)';
  }

  /** Which token the roles are read from, in words. */
  formatRolesSource(source: string | null | undefined): string {
    switch (source) {
      case 'idtoken': return 'from the ID token';
      case 'userinfo': return 'from UserInfo';
      default: return 'from the access token';
    }
  }

  /** How the session-cookie key was chosen, in words. */
  formatSessionKey(key: string | undefined): string {
    switch (key) {
      case 'OIDC_SESSION_SECRET': return 'Dedicated secret (OIDC_SESSION_SECRET)';
      case 'client secret': return 'Derived from the client secret';
      case 'generated at startup': return 'Generated at startup (sessions end on restart)';
      default: return key ?? 'N/A';
    }
  }

  /** The host of a URL ("https://sso.example.com/realms/x" → "sso.example.com"), or the text itself. */
  hostOf(url: string): string {
    try {
      return new URL(url).host;
    } catch {
      return url;
    }
  }

  formatHitRate(rate: number | undefined): string {
    return rate !== undefined ? (rate * 100).toFixed(2) + '%' : 'N/A';
  }

  formatLoadTime(time: number | undefined): string {
    if (time === undefined) return 'N/A';
    if (time < 1000) return time + ' ms';
    return (time / 1000).toFixed(2) + ' s';
  }

  /** Seconds in the largest whole unit: 3600 → "1 hour", 90 → "90 seconds". */
  formatTtl(seconds: number | undefined): string {
    if (seconds === undefined || seconds === null) return 'N/A';
    const units: [number, string][] = [[86400, 'day'], [3600, 'hour'], [60, 'minute']];
    for (const [size, name] of units) {
      if (seconds >= size && seconds % size === 0) {
        const n = seconds / size;
        return `${n} ${name}${n === 1 ? '' : 's'}`;
      }
    }
    return `${seconds} second${seconds === 1 ? '' : 's'}`;
  }

  // Redis cache helper methods
  isCacheEnabled(): boolean {
    return this.redisCacheAvailable &&
           this.redisCacheInfo !== undefined &&
           this.redisCacheInfo.enabled;
  }

  formatCacheHitRate(stats: CacheStats | undefined): string {
    if (!stats) return 'N/A';
    const total = stats.hits + stats.misses;
    if (total === 0) return '0.00%';
    const ratio = stats.hits / total;
    return (ratio * 100).toFixed(2) + '%';
  }

  formatBytes(bytes: number | undefined): string {
    if (bytes === undefined || bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i];
  }

  // SPARQi metrics helper methods
  formatCurrency(amount: number | undefined): string {
    if (amount === undefined) return '$0.00';
    return '$' + amount.toFixed(4);
  }

  /** A cost rounded to cents, for summaries ("$3.21"). */
  formatCost(amount: number | undefined): string {
    return '$' + (amount ?? 0).toFixed(2);
  }

  /** A number with thousands separators and at most the given decimals: 2785.3 → "2,785.3". */
  formatNumber(num: number | undefined, maxDecimals: number = 0): string {
    if (num === undefined) return '0';
    return num.toLocaleString(undefined, { maximumFractionDigits: maxDecimals });
  }
}
