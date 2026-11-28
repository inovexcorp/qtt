import { afterNextRender, ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter, interval, map, startWith } from 'rxjs';
import { MatIcon } from '@angular/material/icon';
import { MatAnchor, MatButton } from '@angular/material/button';
import { MatButtonToggle, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatTooltip } from '@angular/material/tooltip';
import { DatasourceHealth } from '../../core/models/datasource-health';
import { DatasourceLiveService } from '../../core/services/datasource-live.service';
import { IfRoleDirective } from '../../shared/if-role.directive';
import { TweenNumberDirective } from '../../shared/tween-number.directive';
import { HealthPillComponent } from '../../shared/datasource-health/health-pill.component';
import { CheckHistoryComponent } from '../../shared/datasource-health/check-history.component';
import { averageResponseMs, HealthTone, hostOf, relativeTime, statusTone } from '../../shared/datasource-health/health-status';
import { LiveIndicatorComponent } from '../../shared/live-indicator/live-indicator.component';

/** The fleet bar's groups, in display order. */
export type FleetGroup = 'up' | 'down' | 'unchecked' | 'disabled';

export interface FleetSegment {
  key: FleetGroup;
  label: string;
  tone: HealthTone;
  count: number;
}

export function fleetGroup(status: string | null | undefined): FleetGroup {
  switch (status) {
    case 'UP': return 'up';
    case 'DOWN': return 'down';
    case 'DISABLED': return 'disabled';
    default: return 'unchecked';
  }
}

/** One sentence on the state of every datasource, worst news first. */
export function fleetHeadline(segments: FleetSegment[]): string {
  const count = (key: FleetGroup) => segments.find(s => s.key === key)?.count ?? 0;
  const total = segments.reduce((sum, s) => sum + s.count, 0);
  const active = total - count('disabled');
  const down = count('down');
  const unchecked = count('unchecked');
  const plural = (n: number, one: string, many: string) => n === 1 ? one : many;
  if (down > 0) {
    return `${down} of ${active} active ${plural(active, 'datasource', 'datasources')} ${plural(down, 'is', 'are')} down`;
  }
  if (active === 0) {
    return total === 1 ? 'The datasource is disabled' : 'All datasources are disabled';
  }
  if (unchecked === active) {
    return active === 1 ? 'The datasource hasn\'t been checked yet' : 'No datasource has been checked yet';
  }
  if (unchecked > 0) {
    return `${count('up')} up, ${unchecked} not checked yet`;
  }
  return active === 1 ? 'The datasource is up' : `All ${active} active datasources are up`;
}

/**
 * The datasources: one sentence and a bar for the health of all of them, then a card per datasource with its status,
 * its last ten checks and what needs attention. Live: statuses and checks change in place as the server pushes them
 * (see {@link DatasourceLiveService}). The datasource page and the add page render under it (router outlet); the
 * live connection is shared with them.
 */
@Component({
  selector: 'app-datasources',
  templateUrl: './datasources.component.html',
  styleUrls: ['./datasources.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [DatasourceLiveService],
  imports: [RouterLink, RouterOutlet, MatIcon, MatAnchor, MatButton, MatButtonToggleGroup, MatButtonToggle, MatFormField,
    MatLabel, MatSuffix, MatInput, MatTooltip, IfRoleDirective, TweenNumberDirective, HealthPillComponent,
    CheckHistoryComponent, LiveIndicatorComponent],
})
export class DatasourcesComponent {
  protected readonly live = inject(DatasourceLiveService);
  private readonly router = inject(Router);

  private readonly url = toSignal(this.router.events.pipe(
    filter(event => event instanceof NavigationEnd),
    map(() => this.router.url),
    startWith(this.router.url),
  ), { initialValue: this.router.url });
  /** The list shows only at /datasources itself, not under a child page. */
  protected readonly listActive = computed(() => this.url().split(/[?#]/)[0] === '/datasources');

  protected readonly now = toSignal(interval(15000).pipe(map(() => Date.now())), { initialValue: Date.now() });
  /** Set after the first render: entrances and swaps animate only for changes, not on load. */
  protected readonly ready = signal(false);

  protected readonly group = signal<FleetGroup | 'all'>('all');
  protected readonly term = signal('');

  protected readonly segments = computed<FleetSegment[] | null>(() => {
    const all = this.live.datasources();
    if (!all) {
      return null;
    }
    const count = (key: FleetGroup) => all.filter(d => fleetGroup(d.status) === key).length;
    return [
      { key: 'up', label: 'Up', tone: 'good', count: count('up') },
      { key: 'down', label: 'Down', tone: 'bad', count: count('down') },
      { key: 'unchecked', label: 'Not checked', tone: 'idle', count: count('unchecked') },
      { key: 'disabled', label: 'Disabled', tone: 'idle', count: count('disabled') },
    ];
  });

  protected readonly headline = computed(() => {
    const segments = this.segments();
    return segments ? fleetHeadline(segments) : '';
  });

  protected readonly visible = computed(() => {
    const term = this.term().trim().toLowerCase();
    const group = this.group();
    return (this.live.datasources() ?? []).filter(d =>
      (group === 'all' || fleetGroup(d.status) === group)
      && (!term || d.dataSourceId.toLowerCase().includes(term) || d.url.toLowerCase().includes(term)));
  });

  protected readonly skeletons = [0, 1, 2];

  constructor() {
    afterNextRender(() => this.ready.set(true));
  }

  protected tone(ds: DatasourceHealth): HealthTone {
    return statusTone(ds.status);
  }

  protected host(ds: DatasourceHealth): string {
    return hostOf(ds.url);
  }

  protected average(ds: DatasourceHealth): number | null {
    return averageResponseMs(ds.recentChecks);
  }

  protected checked(ds: DatasourceHealth): string {
    return relativeTime(ds.lastHealthCheck, this.now());
  }

  protected clearFilters(): void {
    this.group.set('all');
    this.term.set('');
  }

  protected togglePause(): void {
    if (this.live.status() === 'paused') {
      this.live.resume();
    } else {
      this.live.pause();
    }
  }

  protected readonly liveText = computed(() => liveText(this.live.status(), this.live.transport(), this.live.failed(),
    this.live.updatedAt(), this.now()));
  protected readonly liveAnnouncement = computed(() => liveAnnouncement(this.live.status()));

  protected readonly formatMs = (v: number) => Math.round(v).toLocaleString();
}

/** The live indicator's words. Pushed updates have no "updated … ago": the page is current while it says Live. */
export function liveText(status: string, transport: 'stream' | 'polling', failed: boolean, updatedAt: number | null,
                         now: number): string {
  const ago = updatedAt === null ? '' : relativeTime(new Date(updatedAt).toISOString(), now);
  switch (status) {
    case 'connecting':
      return failed ? 'Not connected · retrying' : 'Connecting…';
    case 'live':
      return transport === 'stream' ? 'Live' : `Live · updated ${ago}`;
    case 'stale':
      return ago ? `Reconnecting · last update ${ago}` : 'Reconnecting…';
    default:
      return 'Paused';
  }
}

export function liveAnnouncement(status: string): string {
  return ({
    connecting: 'Connecting to live health', live: 'Datasource health is live',
    stale: 'Connection lost; reconnecting', paused: 'Live updates paused',
  } as Record<string, string>)[status] ?? '';
}
