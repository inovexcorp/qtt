import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { firstValueFrom, map, startWith } from 'rxjs';
import { Routes } from '../../core/models/routes-interfaces/routes';
import { NewRoute } from '../../core/models/routes-interfaces/new-route';
import { Graphmarts } from '../../core/models/graphmarts';
import { GraphmartLayers } from '../../core/models/graphmart-layers';
import { GraphmartsService } from '../../core/services/graphmarts.service';
import { LayersService } from '../../core/services/layers.service';
import { saveErrorMessage } from '../../core/http-error-message';
import { methodsOf } from '../../shared/query-endpoint';

export const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'] as const;

/** What a route id may contain (the server's RouteIds rule): one plain path segment. */
export const ROUTE_ID_PATTERN = /^[A-Za-z0-9._-]{1,128}$/;
export const ROUTE_ID_RULE = 'Letters, digits, ".", "_" and "-" only (up to 128)';

/** A route id the server accepts, not already taken by one of `taken()`. */
export function routeIdValidator(taken: () => readonly string[]): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const id = (control.value ?? '').trim();
    if (!id) {
      return null; // "required" reports it
    }
    if (!ROUTE_ID_PATTERN.test(id) || id === '.' || id === '..') {
      return { routeId: ROUTE_ID_RULE };
    }
    return taken().includes(id) ? { taken: `A route named ${id} already exists` } : null;
  };
}

/** Loading state of a list fetched from Graph Studio (graphmarts, layers). */
export type FetchState = 'idle' | 'loading' | 'loaded' | 'failed';

/** The saved form of a draft, for spotting unsaved edits: everything that is sent, in a stable order. */
export interface DraftSnapshot {
  routeId: string;
  methods: string[];
  description: string;
  datasourceId: string;
  graphmart: string;
  layers: string[];
  template: string;
  cacheEnabled: boolean;
  cacheTtlSeconds: number | null;
  cacheKeyStrategy: string;
  accessPolicy: string;
  requiredRoles: string;
}

export function sameSnapshot(a: DraftSnapshot | null, b: DraftSnapshot | null): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}

/**
 * A route being written: the new-route page and a route's page edit one of these. It holds the form, the
 * graphmarts and layers its datasource offers (loaded from Graph Studio as the datasource and graphmart change), the
 * chosen layers, and what was last saved, so it can say whether there are unsaved edits.
 *
 * The graphmart is kept as its IRI (the field shows its title) and layers as IRIs, so what is saved and what is
 * tested is the same thing. When Graph Studio can't list them (the datasource is down), the saved values stay and
 * are saved as they are; the fields say why they can't be changed.
 *
 * Provided by the page.
 */
@Injectable()
export class RouteDraft {
  private readonly graphmartsApi = inject(GraphmartsService);
  private readonly layersApi = inject(LayersService);
  private readonly destroyRef = inject(DestroyRef);

  /** Route ids already in use (a new route's id must be free). */
  takenIds: () => readonly string[] = () => [];

  readonly form = new FormGroup({
    routeId: new FormControl('', { nonNullable: true, validators: [Validators.required, routeIdValidator(() => this.takenIds())] }),
    methods: new FormControl<string[]>(['GET'], { nonNullable: true, validators: Validators.required }),
    description: new FormControl('', { nonNullable: true, validators: Validators.required }),
    datasourceId: new FormControl('', { nonNullable: true, validators: Validators.required }),
    /** The graphmart's IRI (the field shows its title). */
    graphmart: new FormControl('', { nonNullable: true, validators: Validators.required }),
    template: new FormControl('', { nonNullable: true }),
    cacheEnabled: new FormControl(false, { nonNullable: true }),
    /** Seconds; empty for the server default. */
    cacheTtlSeconds: new FormControl<number | null>(null, [Validators.min(1)]),
    cacheKeyStrategy: new FormControl('QUERY_HASH', { nonNullable: true }),
    accessPolicy: new FormControl('default', { nonNullable: true }),
    requiredRoles: new FormControl('', { nonNullable: true }),
  });

  /** Chosen layer IRIs (none: every layer of the graphmart). */
  readonly layers = signal<string[]>([]);

  readonly graphmarts = signal<Graphmarts[]>([]);
  readonly graphmartsState = signal<FetchState>('idle');
  readonly graphmartsError = signal<string | null>(null);
  readonly allLayers = signal<GraphmartLayers[]>([]);
  readonly layersState = signal<FetchState>('idle');
  readonly layersError = signal<string | null>(null);

  /** What is saved (null for a new route until it is created). */
  private readonly saved = signal<DraftSnapshot | null>(null);
  /** The form's value, as a signal. */
  private readonly value = toSignal(this.form.valueChanges.pipe(map(() => this.form.getRawValue()), startWith(this.form.getRawValue())),
    { initialValue: this.form.getRawValue() });

  /** The draft as it would be saved now. */
  readonly snapshot = computed<DraftSnapshot>(() => {
    const v = this.value();
    return {
      routeId: v.routeId.trim(),
      methods: [...v.methods].sort(),
      description: v.description,
      datasourceId: v.datasourceId,
      graphmart: v.graphmart,
      layers: [...this.layers()].sort(),
      template: v.template,
      cacheEnabled: v.cacheEnabled,
      cacheTtlSeconds: v.cacheTtlSeconds === null || (v.cacheTtlSeconds as unknown) === '' ? null : Number(v.cacheTtlSeconds),
      cacheKeyStrategy: v.cacheKeyStrategy,
      accessPolicy: v.accessPolicy,
      requiredRoles: v.accessPolicy === 'open' ? '' : v.requiredRoles.trim(),
    };
  });

  /** There are edits that aren't saved. A new route has them once anything is entered. */
  readonly dirty = computed(() => !sameSnapshot(this.snapshot(), this.saved() ?? this.blank()));
  /** A new route before anything was entered. */
  private readonly blank = signal<DraftSnapshot | null>(null);

  /** The fields whose saved value differs, by name (for "Unsaved: template, cache"). */
  readonly changedFields = computed(() => {
    const now = this.snapshot();
    const before = this.saved() ?? this.blank();
    if (!before) {
      return [];
    }
    return (Object.keys(now) as (keyof DraftSnapshot)[])
      .filter(key => JSON.stringify(now[key]) !== JSON.stringify(before[key]));
  });

  private loadingDatasource = '';
  private loadingGraphmart = '';

  constructor() {
    // A different datasource offers different graphmarts; a different graphmart, different layers.
    this.form.controls.datasourceId.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(id => {
      if (id !== this.loadingDatasource) {
        this.form.controls.graphmart.setValue('');
        this.layers.set([]);
        void this.loadGraphmarts(id);
      }
    });
    this.form.controls.graphmart.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(iri => {
      if (iri !== this.loadingGraphmart && this.graphmarts().some(g => g.iri === iri)) {
        this.layers.set([]);
        void this.loadLayers(this.form.controls.datasourceId.value, iri);
      }
    });
  }

  /** Starts a new route: GET, the server's default access, no caching. Nothing entered counts as no edits. */
  startNew(): void {
    this.form.controls.routeId.enable({ emitEvent: false });
    this.blank.set(this.snapshot());
  }

  /** Loads a saved route (with its template and its layers' IRIs) as the saved state. */
  load(route: Routes, template: string, layerIris: string[]): void {
    this.form.controls.routeId.disable({ emitEvent: false }); // a route's id doesn't change
    this.loadingDatasource = route.datasources?.dataSourceId ?? '';
    this.loadingGraphmart = route.graphMartUri ?? '';
    this.form.setValue({
      routeId: route.routeId,
      methods: methodsOf(route.routeParams),
      description: route.description ?? '',
      datasourceId: this.loadingDatasource,
      graphmart: this.loadingGraphmart,
      template: template ?? '',
      cacheEnabled: route.cacheEnabled === true,
      cacheTtlSeconds: route.cacheTtlSeconds ?? null,
      cacheKeyStrategy: route.cacheKeyStrategy || 'QUERY_HASH',
      accessPolicy: route.accessPolicy === 'open' || route.accessPolicy === 'authenticated' ? route.accessPolicy : 'default',
      requiredRoles: route.requiredRoles ?? '',
    });
    this.layers.set([...layerIris]);
    this.markSaved();
    // Locals: loading clears the "loading" markers as it goes
    const datasourceId = this.loadingDatasource;
    const graphmartIri = this.loadingGraphmart;
    void this.loadGraphmarts(datasourceId).then(() => this.loadLayers(datasourceId, graphmartIri));
  }

  /** What is in the form is now what is saved. */
  markSaved(): void {
    this.saved.set(this.snapshot());
    this.form.markAsPristine();
  }

  /** Back to what is saved. */
  discard(): void {
    const saved = this.saved() ?? this.blank();
    if (!saved) {
      return;
    }
    const reloadGraphmarts = saved.datasourceId !== this.form.controls.datasourceId.value;
    const reloadLayers = reloadGraphmarts || saved.graphmart !== this.form.controls.graphmart.value;
    this.loadingDatasource = saved.datasourceId;
    this.loadingGraphmart = saved.graphmart;
    this.form.patchValue({
      routeId: saved.routeId, methods: saved.methods, description: saved.description, datasourceId: saved.datasourceId,
      graphmart: saved.graphmart, template: saved.template, cacheEnabled: saved.cacheEnabled,
      cacheTtlSeconds: saved.cacheTtlSeconds, cacheKeyStrategy: saved.cacheKeyStrategy, accessPolicy: saved.accessPolicy,
      requiredRoles: saved.requiredRoles,
    });
    this.layers.set([...saved.layers]);
    if (reloadGraphmarts) {
      void this.loadGraphmarts(saved.datasourceId).then(() => this.loadLayers(saved.datasourceId, saved.graphmart));
    } else if (reloadLayers) {
      void this.loadLayers(saved.datasourceId, saved.graphmart);
    } else {
      this.loadingDatasource = '';
      this.loadingGraphmart = '';
    }
  }

  // --- Graphmarts and layers ----------------------------------------------------------------------------------------

  async loadGraphmarts(datasourceId: string): Promise<void> {
    this.loadingDatasource = datasourceId;
    this.graphmarts.set([]);
    this.graphmartsError.set(null);
    this.allLayers.set([]);
    this.layersState.set('idle');
    if (!datasourceId) {
      this.graphmartsState.set('idle');
      return;
    }
    this.graphmartsState.set('loading');
    try {
      const list = await firstValueFrom(this.graphmartsApi.getGraphMarts(datasourceId));
      if (this.form.controls.datasourceId.value === datasourceId) {
        this.graphmarts.set(list ?? []);
        this.graphmartsState.set('loaded');
        // The field shows a title for the IRI it holds; it can only look it up now. Written to the view again,
        // without counting as an edit.
        const graphmart = this.form.controls.graphmart;
        graphmart.setValue(graphmart.value, { emitEvent: false });
      }
    } catch (error) {
      if (this.form.controls.datasourceId.value === datasourceId) {
        this.graphmartsState.set('failed');
        this.graphmartsError.set(saveErrorMessage(error));
      }
    } finally {
      this.loadingDatasource = '';
    }
  }

  async loadLayers(datasourceId: string, graphmartIri: string): Promise<void> {
    this.loadingGraphmart = graphmartIri;
    this.allLayers.set([]);
    this.layersError.set(null);
    if (!datasourceId || !graphmartIri) {
      this.layersState.set('idle');
      this.loadingGraphmart = '';
      return;
    }
    this.layersState.set('loading');
    try {
      const list = await firstValueFrom(this.layersApi.getGraphmartLayers(datasourceId, graphmartIri));
      if (this.form.controls.graphmart.value === graphmartIri) {
        this.allLayers.set(list ?? []);
        this.layersState.set('loaded');
      }
    } catch (error) {
      if (this.form.controls.graphmart.value === graphmartIri) {
        this.layersState.set('failed');
        this.layersError.set(saveErrorMessage(error));
      }
    } finally {
      this.loadingGraphmart = '';
    }
  }

  /** A graphmart's title from its IRI (the IRI itself when it isn't listed). */
  graphmartTitle = (iri: string | null | undefined): string =>
    !iri ? '' : this.graphmarts().find(g => g.iri === iri)?.title ?? iri;

  /** A layer's title from its IRI (the IRI itself when it isn't listed). */
  layerTitle(iri: string): string {
    return this.allLayers().find(l => l.iri === iri)?.title ?? iri;
  }

  /** Whether a listed graphmart or layer is active in Graph Studio (null when not listed). */
  graphmartActive(iri: string): boolean | null {
    const g = this.graphmarts().find(x => x.iri === iri);
    return g ? g.active === 'true' : null;
  }

  layerActive(iri: string): boolean | null {
    const l = this.allLayers().find(x => x.iri === iri);
    return l ? l.active === 'true' : null;
  }

  addLayer(iri: string): void {
    if (iri && !this.layers().includes(iri) && this.allLayers().some(l => l.iri === iri)) {
      this.layers.update(list => [...list, iri]);
    }
  }

  removeLayer(iri: string): void {
    this.layers.update(list => list.filter(l => l !== iri));
  }

  // --- Saving -------------------------------------------------------------------------------------------------------

  /** What isn't right yet, in words (empty when it can be saved). */
  readonly problems = computed(() => {
    this.value();
    const c = this.form.controls;
    const problems: string[] = [];
    if (c.routeId.enabled && c.routeId.invalid) {
      problems.push(c.routeId.errors?.['required'] ? 'Name the route' : c.routeId.errors?.['routeId'] ?? c.routeId.errors?.['taken']);
    }
    if (c.methods.invalid) problems.push('Pick at least one HTTP method');
    if (c.description.invalid) problems.push('Describe the route');
    if (c.datasourceId.invalid) problems.push('Pick a datasource');
    if (c.graphmart.invalid) problems.push('Pick a graphmart');
    if (c.cacheTtlSeconds.invalid) problems.push('The cache TTL must be at least 1 second');
    return problems;
  });

  /** The request that creates or updates the route. */
  toRequest(): NewRoute {
    const s = this.snapshot();
    return {
      routeId: s.routeId,
      routeParams: 'httpMethodRestrict=' + s.methods.join(','),
      dataSourceId: s.datasourceId,
      routeDescription: s.description,
      graphMartUri: s.graphmart,
      templateBody: s.template,
      layers: this.layers().join(','),
      cacheEnabled: s.cacheEnabled,
      cacheTtlSeconds: s.cacheTtlSeconds ?? undefined,
      cacheKeyStrategy: s.cacheKeyStrategy,
      accessPolicy: s.accessPolicy,
      requiredRoles: s.requiredRoles,
    };
  }
}
