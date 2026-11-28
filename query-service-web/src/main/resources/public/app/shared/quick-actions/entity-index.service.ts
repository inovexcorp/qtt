import { Injectable, inject, signal } from '@angular/core';
import { catchError, forkJoin, of } from 'rxjs';
import { RoutesService } from '../../pages/routes/routes.service';
import { DatasourceService } from '../../core/services/datasource.service';
import { DatasourceEntry, RouteEntry } from './quick-action';

/** How long a fetched list is reused before the next opening of the bar fetches it again. */
export const REUSE_MS = 30_000;

/**
 * Routes and datasources for the quick actions bar. Nothing is fetched until the bar first opens; after that, the
 * lists are fetched again on an opening at least 30 seconds after the last fetch. No polling.
 */
@Injectable({ providedIn: 'root' })
export class EntityIndexService {

  private readonly routesService = inject(RoutesService);
  private readonly datasourceService = inject(DatasourceService);

  readonly routes = signal<RouteEntry[]>([]);
  readonly datasources = signal<DatasourceEntry[]>([]);
  /** True until the first fetch answers. */
  readonly loading = signal(false);

  private fetchedAt = 0;
  private inFlight = false;

  /** Fetches both lists unless they are fresh (or a fetch is under way). */
  refresh(now: number = Date.now()): void {
    if (this.inFlight || (this.fetchedAt > 0 && now - this.fetchedAt < REUSE_MS)) {
      return;
    }
    this.inFlight = true;
    this.loading.set(this.fetchedAt === 0);
    forkJoin({
      // A list that fails to load is left as it was; the other still updates.
      routes: this.routesService.getRoutes().pipe(catchError(() => of(null))),
      datasources: this.datasourceService.getDatasources().pipe(catchError(() => of(null)))
    }).subscribe(({ routes, datasources }) => {
      if (routes) {
        this.routes.set(routes.map(r => ({
          routeId: r.routeId,
          description: r.description ?? '',
          status: r.status,
          dataSourceId: r.datasources?.dataSourceId ?? ''
        })).sort((a, b) => a.routeId.localeCompare(b.routeId)));
      }
      if (datasources) {
        this.datasources.set(datasources.map(d => ({ dataSourceId: d.dataSourceId, url: d.url, status: d.status }))
          .sort((a, b) => a.dataSourceId.localeCompare(b.dataSourceId)));
      }
      this.fetchedAt = now;
      this.inFlight = false;
      this.loading.set(false);
    });
  }

  /** Makes the next opening fetch again, e.g. after a route was added or deleted. */
  invalidate(): void {
    this.fetchedAt = 0;
  }
}
