import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { of } from 'rxjs';
import { HasUnsavedChanges, unsavedChangesGuard, warnBeforeUnload } from './unsaved-changes.guard';

describe('unsavedChangesGuard', () => {
  let answer: boolean;
  let open: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    answer = false;
    open = vi.fn(() => ({ afterClosed: () => of(answer) }));
    TestBed.configureTestingModule({ providers: [{ provide: MatDialog, useValue: { open } }] });
  });

  const run = (page: HasUnsavedChanges) => TestBed.runInInjectionContext(() =>
    unsavedChangesGuard(page, {} as ActivatedRouteSnapshot, {} as RouterStateSnapshot, {} as RouterStateSnapshot));

  it('lets a page without edits go without asking', () => {
    expect(run({ hasUnsavedChanges: () => false })).toBe(true);
    expect(open).not.toHaveBeenCalled();
  });

  it('asks about a page with edits, and stays when the answer is no', async () => {
    expect(await run({ hasUnsavedChanges: () => true, unsavedSubject: 'route a' })).toBe(false);
    expect(open.mock.calls[0][1].data.message[0]).toContain('route a');
  });

  it('leaves when the user discards', async () => {
    answer = true;
    expect(await run({ hasUnsavedChanges: () => true })).toBe(true);
  });
});

describe('warnBeforeUnload', () => {
  @Component({ template: '' })
  class Page {
    dirty = false;
    constructor() {
      warnBeforeUnload(() => this.dirty);
    }
  }

  const unload = () => {
    const event = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(event);
    return event.defaultPrevented;
  };

  it('has the browser ask only while there are edits, and stops with the page', () => {
    const fixture = TestBed.createComponent(Page);
    expect(unload()).toBe(false);
    fixture.componentInstance.dirty = true;
    expect(unload()).toBe(true);
    fixture.destroy();
    expect(unload()).toBe(false);
  });
});
