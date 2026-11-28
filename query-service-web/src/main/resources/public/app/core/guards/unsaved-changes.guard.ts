import { DestroyRef, inject } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';
import { confirm } from '../../shared/confirm-dialog/confirm-dialog.component';

/** A page that can hold edits that aren't saved yet. */
export interface HasUnsavedChanges {
  hasUnsavedChanges(): boolean;
  /** What the edits are, for the question ("this route", "the new route"). */
  readonly unsavedSubject?: string;
}

/**
 * Asks before leaving a page with unsaved edits (navigating away in the app). Closing or reloading the tab is
 * covered by {@link warnBeforeUnload}, as the browser asks that question itself.
 */
export const unsavedChangesGuard: CanDeactivateFn<HasUnsavedChanges> = component => {
  if (!component?.hasUnsavedChanges?.()) {
    return true;
  }
  const subject = component.unsavedSubject ?? 'this page';
  return firstValueFrom(confirm(inject(MatDialog), {
    title: 'Leave without saving?',
    message: [`Your changes to ${subject} haven't been saved; leaving discards them.`],
    confirmLabel: 'Discard changes',
  }));
};

/**
 * Has the browser ask before the tab is closed or reloaded while `dirty()` is true. Call in an injection context;
 * stops when that context is destroyed.
 */
export function warnBeforeUnload(dirty: () => boolean): void {
  const listener = (event: BeforeUnloadEvent) => {
    if (dirty()) {
      event.preventDefault();
      event.returnValue = ''; // older browsers need it set
    }
  };
  window.addEventListener('beforeunload', listener);
  inject(DestroyRef).onDestroy(() => window.removeEventListener('beforeunload', listener));
}
