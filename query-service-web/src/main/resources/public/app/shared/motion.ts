/** Shared motion helpers. No dependencies, so any page can use them without pulling in a chart library. */

/** The viewer asked for less motion: show the end state of a change without movement. */
export const prefersReducedMotion = (): boolean =>
  typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;

/** Confident deceleration for arrivals (matches $ease-out in the stylesheets). */
export const EASE_OUT = 'cubic-bezier(0.16, 1, 0.3, 1)';

/**
 * One expanding ring from an element (a status dot, a pill) to say "this just changed". Skipped with reduced
 * motion: the change of colour or text is the signal then.
 */
export function ping(element: HTMLElement | undefined | null): void {
  if (element && !prefersReducedMotion() && typeof element.animate === 'function') {
    element.animate(
      [{ transform: 'scale(1)', opacity: 0.6 }, { transform: 'scale(2.8)', opacity: 0 }],
      { duration: 900, easing: EASE_OUT });
  }
}
