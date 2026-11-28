import { afterNextRender, ChangeDetectionStrategy, Component, computed, effect, ElementRef, input, signal, untracked,
  viewChild } from '@angular/core';
import { ping } from '../motion';
import { statusLabel, statusTone } from './health-status';

/**
 * A datasource's health as a tinted pill: dot and word. When the status changes it crossfades to the new tone and
 * the dot sends out one ring, so a live change is noticed without anything moving around it.
 */
@Component({
  selector: 'qtt-health-pill',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class.is-ready]': 'ready()', '[class.qtt-pill--lg]': "size() === 'lg'" },
  template: `
    <span class="qtt-pill" [attr.data-tone]="tone()">
      <span class="qtt-pill__dot" aria-hidden="true"><span class="qtt-pill__ping" #ring></span></span>
      @for (text of [label()]; track text) {
        <span class="qtt-pill__label" animate.enter="qtt-pill-label-in">{{ text }}</span>
      }
    </span>
  `,
  styles: `
    :host { display: inline-flex; flex: none; }
    .qtt-pill {
      --pill-bg: var(--mat-sys-surface-container-high);
      --pill-fg: var(--mat-sys-on-surface-variant);
      --pill-dot: var(--mat-sys-outline);
      display: inline-flex;
      align-items: center;
      gap: 0.4375rem;
      height: 1.5rem;
      padding: 0 0.625rem 0 0.5rem;
      border-radius: 999px;
      background: var(--pill-bg);
      color: var(--pill-fg);
      font: 500 0.75rem/1 var(--mat-sys-body-medium-font), sans-serif;
      letter-spacing: 0.01em;
      white-space: nowrap;
      transition: background-color 250ms ease, color 250ms ease;
    }
    .qtt-pill[data-tone='good'] {
      --pill-bg: color-mix(in srgb, var(--qtt-good) 12%, var(--mat-sys-surface-container-lowest));
      --pill-fg: color-mix(in srgb, var(--qtt-good) 82%, black);
      --pill-dot: var(--qtt-good);
    }
    .qtt-pill[data-tone='bad'] {
      --pill-bg: var(--mat-sys-error-container);
      --pill-fg: var(--mat-sys-on-error-container);
      --pill-dot: var(--mat-sys-error);
    }
    .qtt-pill[data-tone='pending'] {
      --pill-bg: var(--mat-sys-primary-container);
      --pill-fg: var(--mat-sys-on-primary-container);
      --pill-dot: var(--mat-sys-primary);
    }
    .qtt-pill__dot {
      position: relative;
      width: 0.5rem;
      height: 0.5rem;
      border-radius: 50%;
      background: var(--pill-dot);
      transition: background-color 250ms ease;
    }
    .qtt-pill[data-tone='pending'] .qtt-pill__dot { animation: qtt-pill-pulse 1.1s ease-in-out infinite; }
    .qtt-pill__ping {
      position: absolute;
      inset: 0;
      border-radius: 50%;
      background: var(--pill-dot);
      opacity: 0;
      pointer-events: none;
    }
    :host(.qtt-pill--lg) .qtt-pill {
      height: 2rem;
      gap: 0.5rem;
      padding: 0 0.875rem 0 0.75rem;
      font-size: 0.875rem;
    }
    :host(.qtt-pill--lg) .qtt-pill__dot { width: 0.625rem; height: 0.625rem; }
    :host(.is-ready) .qtt-pill-label-in { animation: qtt-pill-label-in 250ms cubic-bezier(0.16, 1, 0.3, 1); }
    @keyframes qtt-pill-label-in { from { opacity: 0; transform: translateY(0.2rem); } }
    @keyframes qtt-pill-pulse { 50% { opacity: 0.35; } }
    @media (prefers-reduced-motion: reduce) {
      .qtt-pill[data-tone='pending'] .qtt-pill__dot { animation: none; }
      :host(.is-ready) .qtt-pill-label-in { animation-duration: 1ms; }
    }
  `,
})
export class HealthPillComponent {
  readonly status = input<string | null | undefined>('UNKNOWN');
  readonly size = input<'sm' | 'lg'>('sm');

  protected readonly label = computed(() => statusLabel(this.status()));
  protected readonly tone = computed(() => statusTone(this.status()));
  /** The first status appears as is; only later changes animate. */
  protected readonly ready = signal(false);
  private readonly ring = viewChild<ElementRef<HTMLElement>>('ring');

  constructor() {
    afterNextRender(() => this.ready.set(true));
    let previous: string | undefined;
    effect(() => {
      const tone = this.tone();
      if (previous !== undefined && previous !== tone) {
        ping(untracked(this.ring)?.nativeElement);
      }
      previous = tone;
    });
  }
}
