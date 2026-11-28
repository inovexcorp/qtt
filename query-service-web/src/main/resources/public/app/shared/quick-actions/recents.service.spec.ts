import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthService } from '../../core/services/auth.service';
import { MAX_RECENTS, RecentsService, entityOf } from './recents.service';

@Component({ template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class Blank {}

describe('RecentsService', () => {
  const key = 'qtt.quickActions.recent.alice';
  let harness: RouterTestingHarness;
  let service: RecentsService;

  beforeEach(async () => {
    localStorage.removeItem(key);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: '**', component: Blank }]),
        { provide: AuthService, useValue: { user: { username: 'alice' } } }
      ]
    });
    harness = await RouterTestingHarness.create();
    service = TestBed.inject(RecentsService);
  });

  afterEach(() => localStorage.removeItem(key));

  it('knows which URLs are route and datasource pages', () => {
    expect(entityOf('/routes/config-route/people-search')).toEqual({ kind: 'route', id: 'people-search' });
    expect(entityOf('/datasources/config-datasource/my%20ds?x=1')).toEqual({ kind: 'datasource', id: 'my ds' });
    expect(entityOf('/routes')).toBeNull();
    expect(entityOf('/settings?section=access')).toBeNull();
  });

  it('records visits newest first, once each, and keeps the last five', async () => {
    for (const id of ['a', 'b', 'a', 'c', 'd', 'e', 'f']) {
      await harness.navigateByUrl('/routes/config-route/' + id);
    }
    await harness.navigateByUrl('/datasources/config-datasource/ds');
    expect(service.entries().map(e => e.id)).toEqual(['ds', 'f', 'e', 'd', 'c']);
    expect(service.entries().length).toBe(MAX_RECENTS);
  });

  it('tracks the page on screen', async () => {
    await harness.navigateByUrl('/routes/config-route/a');
    expect(service.current()).toEqual({ kind: 'route', id: 'a' });
    await harness.navigateByUrl('/metrics');
    expect(service.current()).toBeNull();
  });

  it('remembers them per user across visits, and ignores what it cannot read', async () => {
    await harness.navigateByUrl('/routes/config-route/a');
    expect(JSON.parse(localStorage.getItem(key)!)).toEqual([{ kind: 'route', id: 'a' }]);
    localStorage.setItem(key, JSON.stringify([{ kind: 'route', id: 'kept' }, { kind: 'bogus', id: 'x' }, 'junk']));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: AuthService, useValue: { user: { username: 'alice' } } }] });
    expect(TestBed.inject(RecentsService).entries()).toEqual([{ kind: 'route', id: 'kept' }]);
    localStorage.setItem(key, '{not json');
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: AuthService, useValue: { user: { username: 'alice' } } }] });
    expect(TestBed.inject(RecentsService).entries()).toEqual([]);
  });

  it('works without storage', async () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('quota'); });
    await harness.navigateByUrl('/routes/config-route/a');
    expect(service.entries()).toEqual([{ kind: 'route', id: 'a' }]);
    setItem.mockRestore();
  });
});
