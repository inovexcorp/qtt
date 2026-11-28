import { ChangeDetectionStrategy, Component, computed, ElementRef, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { MatAutocomplete, MatAutocompleteSelectedEvent, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { MatChipGrid, MatChipInput, MatChipRemove, MatChipRow } from '@angular/material/chips';
import { MatError, MatFormField, MatHint, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { MatOption, MatSelect, MatSelectTrigger } from '@angular/material/select';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { DatasourceLiveService } from '../../../core/services/datasource-live.service';
import { statusLabel, statusTone } from '../../../shared/datasource-health/health-status';
import { RouteDraft } from '../route-draft';

/**
 * Where a route's data comes from: the datasource (with its health), the graphmart and the layers, listed from
 * Graph Studio. When Graph Studio can't list them, the saved choice stays and the field says why.
 */
@Component({
  selector: 'qtt-route-data-fields',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatFormField, MatLabel, MatHint, MatError, MatSuffix, MatInput, MatSelect, MatSelectTrigger,
    MatOption, MatIcon, MatAutocomplete, MatAutocompleteTrigger, MatChipGrid, MatChipRow, MatChipRemove, MatChipInput,
    MatProgressSpinner],
  template: `
    <div class="rf-fields" [formGroup]="draft.form">
      <mat-form-field appearance="outline" subscriptSizing="dynamic" class="rf-wide">
        <mat-label>Datasource</mat-label>
        <mat-select formControlName="datasourceId">
          <mat-select-trigger>
            <span class="rf-ds">
              <span class="rf-dot" [attr.data-tone]="tone(draft.form.controls.datasourceId.value)"></span>
              {{ draft.form.controls.datasourceId.value }}
              <span class="rf-ds__status">{{ label(draft.form.controls.datasourceId.value) }}</span>
            </span>
          </mat-select-trigger>
          @for (ds of datasources(); track ds.id) {
            <mat-option [value]="ds.id">
              <span class="rf-ds">
                <span class="rf-dot" [attr.data-tone]="statusTone(ds.status)"></span>
                {{ ds.id }}
                <span class="rf-ds__status">{{ statusLabel(ds.status) }}</span>
              </span>
            </mat-option>
          }
        </mat-select>
        @if (draft.form.controls.datasourceId.invalid) { <mat-error>Pick a datasource</mat-error> }
      </mat-form-field>

      <mat-form-field appearance="outline" subscriptSizing="dynamic" class="rf-wide">
        <mat-label>Graphmart</mat-label>
        <input matInput #gmInput [formControl]="draft.form.controls.graphmart" [matAutocomplete]="gmAuto"
          (input)="gmFilter.set(gmInput.value)" (focus)="gmFilter.set('')" placeholder="Search the datasource's graphmarts">
        @if (draft.graphmartsState() === 'loading') {
          <mat-progress-spinner matSuffix mode="indeterminate" diameter="18" aria-label="Loading graphmarts"></mat-progress-spinner>
        } @else if (graphmartActive() !== null) {
          <mat-icon matSuffix class="rf-active" [class.is-off]="!graphmartActive()"
            [attr.aria-label]="graphmartActive() ? 'Active' : 'Not active'">{{ graphmartActive() ? 'check_circle' : 'pause_circle' }}</mat-icon>
        }
        <mat-autocomplete #gmAuto="matAutocomplete" [displayWith]="draft.graphmartTitle">
          @for (g of filteredGraphmarts(); track g.iri) {
            <mat-option [value]="g.iri">
              <span class="rf-option">
                <span>{{ g.title }}</span>
                @if (g.active !== 'true') { <span class="rf-option__off">not active</span> }
              </span>
            </mat-option>
          }
        </mat-autocomplete>
        @if (draft.graphmartsState() === 'failed') {
          <mat-hint class="rf-warn">Couldn't list graphmarts ({{ draft.graphmartsError() }}); the saved one is kept.</mat-hint>
        } @else if (draft.form.controls.graphmart.value && graphmartActive() === false) {
          <mat-hint class="rf-warn">This graphmart isn't active in Graph Studio, so queries return nothing.</mat-hint>
        }
        @if (draft.form.controls.graphmart.invalid) { <mat-error>Pick a graphmart</mat-error> }
      </mat-form-field>

      <mat-form-field appearance="outline" subscriptSizing="dynamic" class="rf-wide">
        <mat-label>Layers</mat-label>
        <mat-chip-grid #chips aria-label="Layers">
          @for (iri of draft.layers(); track iri) {
            <mat-chip-row (removed)="draft.removeLayer(iri)" [class.is-off]="draft.layerActive(iri) === false">
              {{ draft.layerTitle(iri) }}
              <button matChipRemove [attr.aria-label]="'Remove ' + draft.layerTitle(iri)"><mat-icon>cancel</mat-icon></button>
            </mat-chip-row>
          }
        </mat-chip-grid>
        <input #layerInput [matChipInputFor]="chips" [matAutocomplete]="layerAuto" (input)="layerFilter.set(layerInput.value)"
          [placeholder]="draft.layers().length ? 'Add a layer' : 'Every layer (or pick some)'"
          [disabled]="draft.layersState() !== 'loaded'">
        <mat-autocomplete #layerAuto="matAutocomplete" (optionSelected)="pickLayer($event)">
          @for (l of filteredLayers(); track l.iri) {
            <mat-option [value]="l.iri">
              <span class="rf-option">
                <span>{{ l.title }}</span>
                @if (l.active !== 'true') { <span class="rf-option__off">not active</span> }
              </span>
            </mat-option>
          }
        </mat-autocomplete>
        @if (draft.layersState() === 'failed') {
          <mat-hint class="rf-warn">Couldn't list layers ({{ draft.layersError() }}); the saved ones are kept.</mat-hint>
        } @else if (!draft.layers().length) {
          <mat-hint>None picked: the query runs over every layer of the graphmart.</mat-hint>
        }
      </mat-form-field>
    </div>
  `,
  styleUrl: './route-form.scss',
})
export class RouteDataFieldsComponent {
  protected readonly draft = inject(RouteDraft);
  private readonly health = inject(DatasourceLiveService, { optional: true });
  private readonly layerInput = viewChild<ElementRef<HTMLInputElement>>('layerInput');

  protected readonly statusLabel = statusLabel;
  protected readonly statusTone = statusTone;
  protected readonly gmFilter = signal('');
  protected readonly layerFilter = signal('');
  private readonly graphmartValue = toSignal(this.draft.form.controls.graphmart.valueChanges,
    { initialValue: this.draft.form.controls.graphmart.value });

  /** Datasources to pick from, with their live health; the saved one stays listed if it is gone. */
  protected readonly datasources = computed(() => {
    const list = (this.health?.datasources() ?? []).map(d => ({ id: d.dataSourceId, status: d.status as string }));
    const current = this.draft.form.controls.datasourceId.value;
    return current && !list.some(d => d.id === current) ? [...list, { id: current, status: 'UNKNOWN' }] : list;
  });

  protected readonly filteredGraphmarts = computed(() => {
    const term = this.gmFilter().trim().toLowerCase();
    return this.draft.graphmarts().filter(g => !term || g.title.toLowerCase().includes(term) || g.iri.toLowerCase().includes(term));
  });

  protected readonly filteredLayers = computed(() => {
    const term = this.layerFilter().trim().toLowerCase();
    const chosen = this.draft.layers();
    return this.draft.allLayers().filter(l => !chosen.includes(l.iri) && (!term || l.title.toLowerCase().includes(term)));
  });

  protected readonly graphmartActive = computed(() => {
    this.draft.graphmarts();
    return this.draft.graphmartActive(this.graphmartValue() ?? '');
  });

  protected tone(id: string): string {
    return statusTone(this.datasources().find(d => d.id === id)?.status);
  }

  protected label(id: string): string {
    return statusLabel(this.datasources().find(d => d.id === id)?.status);
  }

  protected pickLayer(event: MatAutocompleteSelectedEvent): void {
    this.draft.addLayer(event.option.value);
    const input = this.layerInput()?.nativeElement;
    if (input) {
      input.value = '';
    }
    this.layerFilter.set('');
  }
}
