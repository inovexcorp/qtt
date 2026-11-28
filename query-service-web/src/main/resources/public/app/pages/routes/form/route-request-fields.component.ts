import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatError, MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatButtonToggle, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { HTTP_METHODS, ROUTE_ID_RULE, RouteDraft } from '../route-draft';

/** A route's request: its id (new routes only), the HTTP methods it answers, and its description. */
@Component({
  selector: 'qtt-route-request-fields',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatFormField, MatLabel, MatHint, MatError, MatInput, MatButtonToggleGroup, MatButtonToggle],
  template: `
    <div class="rf-fields" [formGroup]="draft.form">
      @if (isNew()) {
        <mat-form-field appearance="outline" subscriptSizing="dynamic" class="rf-wide">
          <mat-label>Route name</mat-label>
          <input matInput formControlName="routeId" autocomplete="off" spellcheck="false" class="rf-mono"
            placeholder="people-search">
          <mat-hint>Part of its URL. {{ rule }}</mat-hint>
          @if (draft.form.controls.routeId.errors?.['routeId']; as e) { <mat-error>{{ e }}</mat-error> }
          @if (draft.form.controls.routeId.errors?.['taken']; as e) { <mat-error>{{ e }}</mat-error> }
          @if (draft.form.controls.routeId.errors?.['required']) { <mat-error>Name the route</mat-error> }
        </mat-form-field>
      }
      <div class="rf-methods">
        <span class="rf-label" id="rf-methods-label">HTTP methods</span>
        <mat-button-toggle-group class="qtt-toggle" multiple formControlName="methods" aria-labelledby="rf-methods-label"
          hideMultipleSelectionIndicator>
          @for (m of methods; track m) {
            <mat-button-toggle [value]="m" class="rf-method">{{ m }}</mat-button-toggle>
          }
        </mat-button-toggle-group>
        @if (draft.form.controls.methods.invalid) {
          <span class="rf-error">Pick at least one</span>
        }
      </div>
      <mat-form-field appearance="outline" subscriptSizing="dynamic" class="rf-wide">
        <mat-label>Description</mat-label>
        <input matInput formControlName="description" placeholder="What it answers, for the people who call it">
        @if (draft.form.controls.description.invalid) { <mat-error>Describe the route</mat-error> }
      </mat-form-field>
    </div>
  `,
  styleUrl: './route-form.scss',
})
export class RouteRequestFieldsComponent {
  protected readonly draft = inject(RouteDraft);
  /** A new route: its id can be entered. */
  readonly isNew = input(false);
  protected readonly methods = HTTP_METHODS;
  protected readonly rule = ROUTE_ID_RULE;
}
