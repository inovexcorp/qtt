import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { firstValueFrom, map } from 'rxjs';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CacheInfo } from '../../core/models/cache-info';
import { CacheService } from '../../core/services/cache.service';
import { saveErrorMessage } from '../../core/http-error-message';
import { HasUnsavedChanges, warnBeforeUnload } from '../../core/guards/unsaved-changes.guard';
import { RouteAccessComponent } from '../../shared/route-access/route-access.component';
import { EndpointPanelComponent } from '../../shared/endpoint-panel/endpoint-panel.component';
import { effectiveAccess } from '../../shared/query-endpoint';
import { routeLink } from '../../shared/route-links';
import { RouteDraft } from '../routes/route-draft';
import { RoutesService } from '../routes/routes.service';
import { RoutesLiveService } from '../routes/routes-live.service';
import { RouteRequestFieldsComponent } from '../routes/form/route-request-fields.component';
import { RouteDataFieldsComponent } from '../routes/form/route-data-fields.component';
import { RouteCacheFieldsComponent } from '../routes/form/route-cache-fields.component';
import { RouteTemplateEditorComponent } from '../routes/form/route-template-editor.component';

/** GET /routes/cache/info: the cache, when the server has one. */
export interface CacheInfoResponse {
  available?: boolean;
  info?: CacheInfo;
}

/** The server's cache from its info response: null when it has none. */
export function cacheOf(response: CacheInfoResponse | null | undefined): CacheInfo | null {
  return response?.info?.enabled ? response.info : null;
}

/**
 * A new route, on its own page: what it answers (name, methods, description, with its URL as you type), where the
 * data comes from, the template, who may call it, and caching. Creating it opens its page. Leaving with something
 * entered asks first.
 */
@Component({
  selector: 'app-add-route',
  templateUrl: './add-route.component.html',
  styleUrls: ['./add-route.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [RouteDraft],
  imports: [MatButton, MatIcon, RouteAccessComponent, EndpointPanelComponent, RouteRequestFieldsComponent,
    RouteDataFieldsComponent, RouteCacheFieldsComponent, RouteTemplateEditorComponent],
})
export class AddRouteComponent implements HasUnsavedChanges {
  protected readonly draft = inject(RouteDraft);
  private readonly api = inject(RoutesService);
  private readonly live = inject(RoutesLiveService, { optional: true });
  private readonly cache = inject(CacheService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  readonly unsavedSubject = 'the new route';

  protected readonly cacheInfo = signal<CacheInfo | null | undefined>(undefined);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Create was pressed: say what is missing. */
  protected readonly attempted = signal(false);
  private created = false;

  private readonly value = toSignal(this.draft.form.valueChanges.pipe(map(() => this.draft.form.getRawValue())),
    { initialValue: this.draft.form.getRawValue() });
  protected readonly routeId = computed(() => this.value().routeId.trim());
  protected readonly baseUrl = computed(() => this.live?.baseUrl() ?? '');
  protected readonly access = computed(() =>
    effectiveAccess(this.value().accessPolicy, this.live?.serverDefaultAccess() ?? 'open'));
  protected readonly methods = computed(() => this.value().methods);

  constructor() {
    this.draft.takenIds = () => (this.live?.routes() ?? []).map(r => r.routeId);
    this.draft.startNew();
    // The list of taken ids may arrive after a name was typed
    effect(() => {
      this.live?.routes();
      untracked(() => this.draft.form.controls.routeId.updateValueAndValidity({ emitEvent: true }));
    });
    warnBeforeUnload(() => this.hasUnsavedChanges());
    this.cache.getCacheInfo().subscribe({
      next: response => this.cacheInfo.set(cacheOf(response as unknown as CacheInfoResponse)),
      error: () => this.cacheInfo.set(null),
    });
  }

  hasUnsavedChanges(): boolean {
    return !this.created && this.draft.dirty();
  }

  protected async create(): Promise<void> {
    this.attempted.set(true);
    this.draft.form.markAllAsTouched();
    if (this.draft.problems().length || this.saving()) {
      return;
    }
    const request = this.draft.toRequest();
    this.saving.set(true);
    this.error.set(null);
    try {
      await firstValueFrom(this.api.createRoute(request), { defaultValue: null });
      this.created = true;
      const stopped = !request.templateBody.trim();
      this.snackBar.open(stopped ? `Created ${request.routeId}. It stays stopped until it has a template.`
        : `Created ${request.routeId}.`, 'Close', { duration: 5000 });
      void this.live?.load();
      await this.router.navigate(routeLink(request.routeId));
    } catch (error) {
      this.error.set(saveErrorMessage(error));
    } finally {
      this.saving.set(false);
    }
  }

  protected cancel(): void {
    void this.router.navigate(['/routes']);
  }
}
