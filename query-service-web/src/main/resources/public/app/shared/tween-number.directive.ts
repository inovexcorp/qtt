import { DestroyRef, Directive, effect, ElementRef, inject, input, untracked } from '@angular/core';
import { prefersReducedMotion } from './motion';

const DURATION_MS = 600;
const easeOutExpo = (t: number) => (t >= 1 ? 1 : 1 - Math.pow(2, -10 * t));

/**
 * Shows a number, and when it changes counts from the old value to the new one, so a live refresh reads as the
 * figure moving. The first value appears as is (no count-up from zero while the page loads); with reduced motion,
 * every value does.
 *
 *   <span [qttTweenNumber]="summary.avgProcessingMs" [format]="formatMs"></span>
 */
@Directive({ selector: '[qttTweenNumber]' })
export class TweenNumberDirective {
  readonly qttTweenNumber = input.required<number>();
  readonly format = input<(value: number) => string>(v => Math.round(v).toLocaleString());

  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private shown: number | null = null;
  private frame = 0;

  constructor() {
    effect(() => {
      const target = this.qttTweenNumber();
      const format = this.format();
      untracked(() => this.animateTo(target, format));
    });
    inject(DestroyRef).onDestroy(() => cancelAnimationFrame(this.frame));
  }

  private animateTo(target: number, format: (value: number) => string): void {
    cancelAnimationFrame(this.frame);
    const from = this.shown;
    if (from === null || from === target || !Number.isFinite(from) || prefersReducedMotion()) {
      this.render(target, format);
      return;
    }
    // Timed from the first frame: a frame's timestamp can be earlier than performance.now() read here, and a
    // negative progress would make the ease run backwards (the figure would dip before rising).
    let start: number | null = null;
    const step = (now: number) => {
      start ??= now;
      const t = Math.min(1, Math.max(0, (now - start) / DURATION_MS));
      this.render(from + (target - from) * easeOutExpo(t), format);
      if (t < 1) {
        this.frame = requestAnimationFrame(step);
      } else {
        this.render(target, format);
      }
    };
    this.frame = requestAnimationFrame(step);
  }

  private render(value: number, format: (value: number) => string): void {
    this.shown = value;
    this.el.textContent = format(value);
  }
}
