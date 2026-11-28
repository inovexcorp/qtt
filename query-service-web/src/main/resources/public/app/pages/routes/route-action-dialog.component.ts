import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogActions, MatDialogContent, MatDialogRef, MatDialogTitle } from '@angular/material/dialog';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { formatRate } from './routes-derive';

/** A route the dialog asks about, with its recent calls per minute (to warn when it is in use). */
export interface DialogTarget {
  id: string;
  perMinute?: number | null;
}

/** What running the action on some routes did. */
export interface DialogRun {
  done: string[];
  failed: { id: string; message: string }[];
}

export interface RouteActionDialogData {
  action: 'stop' | 'delete';
  targets: DialogTarget[];
  /** Runs the action on these routes (the caller's requests); the dialog shows progress and failures. */
  perform(ids: string[]): Promise<DialogRun>;
}

/**
 * Asks before stopping or deleting routes, then does it in place: a spinner while the requests run, and when some
 * fail, which ones and why, with a retry for just those. Closes with what was done (undefined when cancelled before
 * anything ran). Routes in use are flagged with their call rate, since callers will get errors.
 */
@Component({
  selector: 'app-route-action-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogTitle, MatDialogContent, MatDialogActions, MatButton, MatIcon, MatProgressSpinner],
  template: `
    <div class="qtt-dialog">
      <h2 mat-dialog-title>{{ title() }}</h2>
      <mat-dialog-content>
        <p class="qtt-dialog__note">{{ consequence() }}</p>
        @if (data.targets.length > 1) {
          <ul class="qtt-dialog__chips" [attr.aria-label]="'Routes to ' + data.action">
            @for (t of data.targets; track t.id) {
              <li [class.is-busy]="busy(t)" [class.is-done]="done().includes(t.id)">
                {{ t.id }}@if (busy(t)) {<span class="rad-rate">&nbsp;· {{ rate(t) }}</span>}
              </li>
            }
          </ul>
        }
        @if (inUse().length) {
          <p class="rad-warning">
            <mat-icon aria-hidden="true">warning_amber</mat-icon>
            <span>{{ inUseText() }}</span>
          </p>
        }
        @if (failed().length) {
          <div class="qtt-dialog__error" role="alert">
            <mat-icon aria-hidden="true">error_outline</mat-icon>
            <div>
              <strong>{{ failedTitle() }}</strong>
              <ul class="rad-failures">
                @for (f of failed(); track f.id) {
                  <li><span class="rad-id">{{ f.id }}</span>: {{ f.message }}</li>
                }
              </ul>
            </div>
          </div>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button (click)="close()" [disabled]="running()" cdkFocusInitial>
          {{ done().length ? 'Close' : 'Cancel' }}
        </button>
        <button mat-flat-button class="qtt-dialog__danger" (click)="confirm()" [disabled]="running()">
          @if (running()) {
            <mat-progress-spinner mode="indeterminate" diameter="18" aria-label="Working"></mat-progress-spinner>
            {{ data.action === 'stop' ? 'Stopping…' : 'Deleting…' }}
          } @else {
            {{ confirmLabel() }}
          }
        </button>
      </mat-dialog-actions>
    </div>
  `,
  styles: [`
    .rad-warning {
      display: flex; align-items: flex-start; gap: 0.5rem; margin: 0; padding: 0.625rem 0.75rem; border-radius: 8px;
      background: color-mix(in srgb, var(--qtt-warn) 10%, var(--mat-sys-surface-container-lowest));
      font-size: 0.875rem; color: var(--mat-sys-on-surface);
      mat-icon { flex: none; color: var(--qtt-warn); }
    }
    .rad-rate { color: var(--qtt-warn); font-variant-numeric: tabular-nums; }
    .qtt-dialog__chips li.is-done { text-decoration: line-through; color: var(--qtt-muted); }
    .rad-failures { margin: 0.25rem 0 0; padding-left: 1rem; }
    .rad-id { font-family: ui-monospace, 'SF Mono', Menlo, Consolas, monospace; }
  `],
})
export class RouteActionDialogComponent {
  protected readonly data = inject<RouteActionDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<RouteActionDialogComponent, DialogRun | undefined>>(MatDialogRef);

  protected readonly running = signal(false);
  protected readonly done = signal<string[]>([]);
  protected readonly failed = signal<DialogRun['failed']>([]);

  private readonly one = this.data.targets.length === 1;
  private readonly verb = this.data.action === 'stop' ? 'Stop' : 'Delete';

  protected readonly title = computed(() => this.one
    ? `${this.verb} ${this.data.targets[0].id}?` : `${this.verb} ${this.data.targets.length} routes?`);

  protected readonly consequence = computed(() => {
    const n = this.data.targets.length;
    if (this.data.action === 'stop') {
      return this.one
        ? 'It will stop answering: callers get an error until it is started again.'
        : `They will stop answering: callers get an error until they are started again.`;
    }
    return this.one
      ? 'The route, its template and its settings will be deleted. This can\'t be undone.'
      : `These ${n} routes, with their templates and settings, will be deleted. This can't be undone.`;
  });

  protected readonly inUse = computed(() => this.data.targets.filter(t => this.busy(t)));
  protected readonly inUseText = computed(() => {
    const busy = this.inUse();
    if (this.one) {
      return `It is in use: ${this.rate(busy[0])} over the last few minutes.`;
    }
    return `${busy.length} of them ${busy.length === 1 ? 'is' : 'are'} in use, so their callers will get errors.`;
  });

  protected readonly confirmLabel = computed(() => {
    if (this.failed().length) {
      return this.failed().length === 1 ? 'Try again' : `Try the ${this.failed().length} again`;
    }
    return this.one ? `${this.verb} route` : `${this.verb} ${this.data.targets.length} routes`;
  });

  protected readonly failedTitle = computed(() => {
    const f = this.failed().length;
    const verb = this.data.action === 'stop' ? 'stop' : 'delete';
    return this.one ? `Couldn't ${verb} it.` : `Couldn't ${verb} ${f} of ${this.data.targets.length}.`;
  });

  protected busy(t: DialogTarget): boolean {
    return (t.perMinute ?? 0) > 0;
  }

  protected rate(t: DialogTarget): string {
    return formatRate(t.perMinute ?? null);
  }

  protected async confirm(): Promise<void> {
    const ids = this.failed().length ? this.failed().map(f => f.id) : this.data.targets.map(t => t.id);
    this.running.set(true);
    this.dialogRef.disableClose = true;
    try {
      const run = await this.data.perform(ids);
      this.done.update(done => [...done, ...run.done]);
      this.failed.set(run.failed);
    } finally {
      this.running.set(false);
      this.dialogRef.disableClose = false;
    }
    if (!this.failed().length) {
      this.close();
    }
  }

  protected close(): void {
    this.dialogRef.close(this.done().length || this.failed().length
      ? { done: this.done(), failed: this.failed() } : undefined);
  }
}
