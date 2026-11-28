import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { GlobalShortcutsService, Shortcut, isTyping, shortcutFor } from './global-shortcuts.service';

function key(init: KeyboardEventInit, target?: HTMLElement): KeyboardEvent {
  const event = new KeyboardEvent('keydown', { bubbles: true, cancelable: true, ...init });
  if (target) {
    Object.defineProperty(event, 'target', { value: target });
  }
  return event;
}

describe('quick actions shortcuts', () => {
  const input = document.createElement('input');
  const checkbox = Object.assign(document.createElement('input'), { type: 'checkbox' });
  const textarea = document.createElement('textarea');
  const button = document.createElement('button');
  const monaco = document.createElement('div');
  monaco.className = 'monaco-editor';
  const inMonaco = document.createElement('textarea');
  monaco.appendChild(inMonaco);
  const editable = document.createElement('div');
  editable.contentEditable = 'true';
  document.body.appendChild(editable);

  afterAll(() => editable.remove());

  it('opens on Cmd+K and Ctrl+K anywhere, including fields and Monaco', () => {
    for (const target of [button, input, textarea, inMonaco]) {
      expect(shortcutFor(key({ key: 'k', metaKey: true }, target), false)).toBe('open');
      expect(shortcutFor(key({ key: 'K', ctrlKey: true }, target), false)).toBe('open');
    }
  });

  it('ignores Cmd+Shift+K and Cmd+Alt+K', () => {
    expect(shortcutFor(key({ key: 'k', metaKey: true, shiftKey: true }, button), false)).toBeNull();
    expect(shortcutFor(key({ key: 'k', ctrlKey: true, altKey: true }, button), false)).toBeNull();
  });

  it('opens on "/" and shows help on "?" only outside fields and editors', () => {
    expect(shortcutFor(key({ key: '/' }, button), false)).toBe('open');
    expect(shortcutFor(key({ key: '?', shiftKey: true }, button), false)).toBe('help');
    expect(shortcutFor(key({ key: '/' }, checkbox), false)).toBe('open');
    for (const target of [input, textarea, inMonaco, editable]) {
      expect(shortcutFor(key({ key: '/' }, target), false)).toBeNull();
      expect(shortcutFor(key({ key: '?', shiftKey: true }, target), false)).toBeNull();
    }
    expect(shortcutFor(key({ key: '/', ctrlKey: true }, button), false)).toBeNull();
  });

  it('does nothing while a dialog is open', () => {
    expect(shortcutFor(key({ key: 'k', metaKey: true }, button), true)).toBeNull();
    expect(shortcutFor(key({ key: '/' }, button), true)).toBeNull();
  });

  it('knows what counts as typing', () => {
    expect(isTyping(input)).toBe(true);
    expect(isTyping(checkbox)).toBe(false);
    expect(isTyping(button)).toBe(false);
    expect(isTyping(editable)).toBe(true);
    expect(isTyping(null)).toBe(false);
  });

  describe('GlobalShortcutsService', () => {
    let openDialogs: unknown[];
    let service: GlobalShortcutsService;
    let seen: Shortcut[];

    beforeEach(() => {
      openDialogs = [];
      TestBed.configureTestingModule({ providers: [{ provide: MatDialog, useValue: { get openDialogs() { return openDialogs; } } }] });
      service = TestBed.inject(GlobalShortcutsService);
      seen = [];
      service.shortcut$.subscribe(s => seen.push(s));
      service.start();
    });

    it('catches Cmd+K before the focused element sees it, and cancels it', () => {
      const target = document.createElement('textarea');
      document.body.appendChild(target);
      let reachedTarget = false;
      target.addEventListener('keydown', () => reachedTarget = true);
      const event = new KeyboardEvent('keydown', { key: 'k', metaKey: true, bubbles: true, cancelable: true });
      target.dispatchEvent(event);
      expect(seen).toEqual(['open']);
      expect(reachedTarget).toBe(false);
      expect(event.defaultPrevented).toBe(true);
      target.remove();
    });

    it('can cancel the key even when a passive capturing listener is already on the document (CDK focus tracking)', () => {
      // Start a fresh service after the passive listener, the order the app has them in.
      TestBed.resetTestingModule();
      const passive = () => undefined;
      document.addEventListener('keydown', passive, { capture: true, passive: true });
      TestBed.configureTestingModule({ providers: [{ provide: MatDialog, useValue: { openDialogs: [] } }] });
      service = TestBed.inject(GlobalShortcutsService);
      service.shortcut$.subscribe(s => seen.push(s));
      service.start();
      const event = new KeyboardEvent('keydown', { key: 'k', ctrlKey: true, bubbles: true, cancelable: true });
      document.body.dispatchEvent(event);
      document.removeEventListener('keydown', passive, { capture: true });
      expect(seen).toEqual(['open']);
      expect(event.defaultPrevented).toBe(true);
    });

    it('lets other keys through', () => {
      const event = new KeyboardEvent('keydown', { key: 'a', bubbles: true, cancelable: true });
      document.body.dispatchEvent(event);
      expect(seen).toEqual([]);
      expect(event.defaultPrevented).toBe(false);
    });

    it('stays quiet while a dialog is open', () => {
      openDialogs = [{}];
      document.body.dispatchEvent(new KeyboardEvent('keydown', { key: '/', bubbles: true, cancelable: true }));
      expect(seen).toEqual([]);
    });

    it('stops listening when destroyed', () => {
      TestBed.resetTestingModule();
      document.body.dispatchEvent(new KeyboardEvent('keydown', { key: '/', bubbles: true, cancelable: true }));
      expect(seen).toEqual([]);
    });
  });
});
