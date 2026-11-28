import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { MatError, MatFormField, MatHint, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { MatIcon } from '@angular/material/icon';
import { CacheInfo } from '../../../core/models/cache-info';
import { RouteDraft } from '../route-draft';

/** "1 h", "90 s", "2 h 30 min", "1 day". */
export function formatDuration(seconds: number | null | undefined): string {
  if (!seconds || seconds <= 0) {
    return '';
  }
  if (seconds % 86400 === 0) {
    const days = seconds / 86400;
    return days === 1 ? '1 day' : `${days} days`;
  }
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  return [h ? `${h} h` : '', m ? `${m} min` : '', s ? `${s} s` : ''].filter(Boolean).join(' ');
}

/**
 * Whether a route's results are cached, for how long, and keyed on what. The settings can be edited even when the
 * server has no cache (they apply once it has one); the note says so.
 */
@Component({
  selector: 'qtt-route-cache-fields',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatFormField, MatLabel, MatHint, MatError, MatSuffix, MatInput, MatSelect, MatOption,
    MatSlideToggle, MatIcon],
  template: `
    <div class="rf-fields" [formGroup]="draft.form">
      @if (info() === null) {
        <p class="rf-note"><mat-icon aria-hidden="true">info</mat-icon>
          This server has no query cache set up. The settings are kept and apply once it has one.</p>
      } @else if (info() && !info()!.connected) {
        <p class="rf-note rf-note--warn"><mat-icon aria-hidden="true">warning_amber</mat-icon>
          The query cache isn't reachable right now{{ info()!.errorMessage ? ': ' + info()!.errorMessage : '' }}. Queries go to the datasource meanwhile.</p>
      }
      <mat-slide-toggle formControlName="cacheEnabled" class="rf-toggle">
        Cache this route's results
      </mat-slide-toggle>
      @if (enabled()) {
        <div class="rf-row">
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>Keep results for</mat-label>
            <input matInput type="number" min="1" formControlName="cacheTtlSeconds" [placeholder]="defaultTtl() ? '' + defaultTtl() : 'server default'">
            <span matTextSuffix>seconds</span>
            <mat-hint>{{ ttlHint() }}</mat-hint>
            <mat-error>At least 1 second</mat-error>
          </mat-form-field>
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>A cached result is reused for</mat-label>
            <mat-select formControlName="cacheKeyStrategy">
              <mat-option value="QUERY_HASH">The same SPARQL query</mat-option>
              <mat-option value="ROUTE_PARAMS">The same request parameters</mat-option>
            </mat-select>
            <mat-hint>{{ strategyHint() }}</mat-hint>
          </mat-form-field>
        </div>
      }
    </div>
  `,
  styleUrl: './route-form.scss',
})
export class RouteCacheFieldsComponent {
  protected readonly draft = inject(RouteDraft);
  /** The server's cache: undefined while loading, null when it has none. */
  readonly info = input<CacheInfo | null | undefined>(undefined);

  private readonly value = toSignal(this.draft.form.valueChanges, { initialValue: this.draft.form.getRawValue() });
  protected readonly enabled = computed(() => this.value().cacheEnabled === true);
  protected readonly defaultTtl = computed(() => this.info()?.defaultTtlSeconds ?? null);

  protected readonly ttlHint = computed(() => {
    const ttl = this.value().cacheTtlSeconds;
    if (ttl) {
      return formatDuration(Number(ttl));
    }
    const fallback = formatDuration(this.defaultTtl());
    return fallback ? `Empty: the server default, ${fallback}` : 'Empty: the server default';
  });

  protected readonly strategyHint = computed(() => this.value().cacheKeyStrategy === 'ROUTE_PARAMS'
    ? 'Cheaper to look up; right when the parameters fully decide the query'
    : 'Safest: two requests share a result only if they build the same query');
}
