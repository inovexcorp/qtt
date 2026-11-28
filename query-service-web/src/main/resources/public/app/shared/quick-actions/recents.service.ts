import { Injectable, inject, signal } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { RecentEntry } from './search';

/** How many recent pages the bar remembers. */
export const MAX_RECENTS = 5;

const PAGES: { kind: RecentEntry['kind']; pattern: RegExp }[] = [
  { kind: 'route', pattern: /^\/routes\/config-route\/([^/?#]+)/ },
  { kind: 'datasource', pattern: /^\/datasources\/config-datasource\/([^/?#]+)/ }
];

/** The route or datasource page a URL shows, if any. */
export function entityOf(url: string): RecentEntry | null {
  for (const { kind, pattern } of PAGES) {
    const m = pattern.exec(url);
    if (m) {
      return { kind, id: decodeURIComponent(m[1]) };
    }
  }
  return null;
}

/**
 * The route and datasource pages opened lately, however they were opened (bar, table, link), newest first. Kept per
 * user in localStorage; if storage is unavailable, only for this visit.
 */
@Injectable({ providedIn: 'root' })
export class RecentsService {

  private readonly router = inject(Router);
  private readonly storageKey = 'qtt.quickActions.recent.' + inject(AuthService).user.username;

  /** Newest first, including the page on screen. */
  readonly entries = signal<RecentEntry[]>(this.read());
  /** The route or datasource page on screen, if any. */
  readonly current = signal<RecentEntry | null>(null);

  constructor() {
    if (this.router.navigated) {
      this.visit(this.router.url);
    }
    this.router.events.pipe(filter(e => e instanceof NavigationEnd))
      .subscribe(e => this.visit((e as NavigationEnd).urlAfterRedirects));
  }

  private visit(url: string): void {
    const entry = entityOf(url);
    this.current.set(entry);
    if (!entry) {
      return;
    }
    const rest = this.entries().filter(e => e.kind !== entry.kind || e.id !== entry.id);
    this.entries.set([entry, ...rest].slice(0, MAX_RECENTS));
    this.write();
  }

  private read(): RecentEntry[] {
    try {
      const parsed = JSON.parse(localStorage.getItem(this.storageKey) ?? '[]');
      return Array.isArray(parsed)
        ? parsed.filter(e => (e?.kind === 'route' || e?.kind === 'datasource') && typeof e.id === 'string')
          .slice(0, MAX_RECENTS)
        : [];
    } catch {
      return [];
    }
  }

  private write(): void {
    try {
      localStorage.setItem(this.storageKey, JSON.stringify(this.entries()));
    } catch {
      // storage unavailable or full: remembered for this visit only
    }
  }
}
