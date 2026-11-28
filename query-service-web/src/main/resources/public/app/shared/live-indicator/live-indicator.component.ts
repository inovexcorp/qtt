import { ChangeDetectionStrategy, Component, effect, ElementRef, input, output, untracked, viewChild } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatIconButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { LiveStatus } from '../../core/services/metrics-live.service';
import { ping } from '../motion';

/**
 * The live-data status of a page: a dot (green live, amber stale, grey paused) that sends out one ring whenever new
 * data lands, the state in words, and pause / refresh. Styled by .qtt-live (styles/_surfaces.scss).
 */
@Component({
  selector: 'qtt-live-indicator',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIcon, MatIconButton, MatTooltip],
  template: `
    <div class="qtt-live" [attr.data-status]="status()">
      <span class="qtt-live__dot" aria-hidden="true"><span class="qtt-live__ping" #ring></span></span>
      <span class="qtt-live__text">{{ text() }}</span>
      <span class="qtt-visually-hidden" role="status">{{ announcement() }}</span>
      <button mat-icon-button (click)="togglePause.emit()"
        [matTooltip]="status() === 'paused' ? 'Resume live updates' : 'Pause live updates'"
        [attr.aria-label]="status() === 'paused' ? 'Resume live updates' : 'Pause live updates'">
        <mat-icon>{{ status() === 'paused' ? 'play_arrow' : 'pause' }}</mat-icon>
      </button>
      <button mat-icon-button (click)="refresh.emit()" matTooltip="Refresh now" aria-label="Refresh now">
        <mat-icon>refresh</mat-icon>
      </button>
    </div>
  `,
})
export class LiveIndicatorComponent {
  readonly status = input.required<LiveStatus>();
  readonly text = input.required<string>();
  /** For screen readers; announced when it changes (keep it free of ticking clocks). */
  readonly announcement = input('');
  /** Changes when new data lands (e.g. its arrival time): the dot pings. */
  readonly landed = input<number | null>(null);
  readonly togglePause = output<void>();
  readonly refresh = output<void>();

  private readonly ring = viewChild<ElementRef<HTMLElement>>('ring');

  constructor() {
    effect(() => {
      if (this.landed() !== null) {
        ping(untracked(this.ring)?.nativeElement);
      }
    });
  }
}
