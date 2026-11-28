import { DOCUMENT, Injectable, NgZone, OnDestroy, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Subject } from 'rxjs';

/** What a key press asks the quick actions bar to do. */
export type Shortcut = 'open' | 'help';

/**
 * The bar's keyboard shortcuts, decided from a keydown:
 * - Cmd+K / Ctrl+K opens the bar from anywhere, including inside an input or the Monaco editor;
 * - "/" opens it, and "?" opens the shortcut list, unless the user is typing in a field or editor;
 * - nothing while a dialog is open, so the bar never opens behind a modal.
 */
export function shortcutFor(event: KeyboardEvent, dialogOpen: boolean): Shortcut | null {
  if (dialogOpen || event.isComposing) {
    return null;
  }
  const mod = event.metaKey || event.ctrlKey;
  if (mod && !event.altKey && !event.shiftKey && event.key.toLowerCase() === 'k') {
    return 'open';
  }
  if (mod || event.altKey || isTyping(event.target)) {
    return null;
  }
  if (event.key === '/') {
    return 'open';
  }
  if (event.key === '?') {
    return 'help';
  }
  return null;
}

/** True when the key goes into a text field, a select, an editable element or the Monaco editor. */
export function isTyping(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) {
    return false;
  }
  if (target.isContentEditable || target.closest('.monaco-editor')) {
    return true;
  }
  if (target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement) {
    return true;
  }
  if (target instanceof HTMLInputElement) {
    return !['checkbox', 'radio', 'button', 'submit', 'reset', 'range', 'color', 'file'].includes(target.type);
  }
  return false;
}

/**
 * Listens for the bar's shortcuts on the window. The listener runs in the capture phase, before the key reaches its
 * target, so Cmd/Ctrl+K opens the bar even when the Monaco editor has focus (Monaco's Cmd+K chords are given up for
 * this).
 */
@Injectable({ providedIn: 'root' })
export class GlobalShortcutsService implements OnDestroy {

  private readonly window = inject(DOCUMENT).defaultView!;
  private readonly zone = inject(NgZone);
  private readonly dialog = inject(MatDialog);
  private readonly triggered = new Subject<Shortcut>();
  readonly shortcut$ = this.triggered.asObservable();
  private started = false;

  private readonly onKeydown = (event: KeyboardEvent) => {
    const shortcut = shortcutFor(event, this.dialog.openDialogs.length > 0);
    if (!shortcut) {
      return;
    }
    event.preventDefault();
    event.stopPropagation();
    this.zone.run(() => this.triggered.next(shortcut));
  };

  /** Starts listening (once). Outside the Angular zone: other key presses cause no change detection. */
  start(): void {
    if (this.started) {
      return;
    }
    this.started = true;
    // On the window, not the document: zone.js shares one native listener per target, event and phase, with the
    // options of the first one added, and the CDK's focus tracking adds a passive capturing keydown listener to the
    // document. Sharing it would make preventDefault() a no-op, so the browser would still act on the key (Ctrl+K
    // focuses the address bar).
    this.zone.runOutsideAngular(() => this.window.addEventListener('keydown', this.onKeydown, { capture: true, passive: false }));
  }

  ngOnDestroy(): void {
    this.window.removeEventListener('keydown', this.onKeydown, { capture: true });
  }
}
