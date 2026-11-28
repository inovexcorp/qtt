import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Component, signal } from '@angular/core';

import { DatasourcesComponent, FleetSegment, fleetHeadline, liveText } from './datasources.component';
import { DatasourceLiveService } from '../../core/services/datasource-live.service';
import { DatasourceHealth } from '../../core/models/datasource-health';

const ds = (id: string, status: DatasourceHealth['status'], extra: Partial<DatasourceHealth> = {}): DatasourceHealth => ({
  dataSourceId: id, url: `https://${id}.example.com:8443`, status, lastHealthCheck: null, lastHealthError: null,
  consecutiveFailures: 0, routeCount: 1, recentChecks: [], ...extra,
});

const segments = (up: number, down: number, unchecked: number, disabled: number): FleetSegment[] => [
  { key: 'up', label: 'Up', tone: 'good', count: up },
  { key: 'down', label: 'Down', tone: 'bad', count: down },
  { key: 'unchecked', label: 'Not checked', tone: 'idle', count: unchecked },
  { key: 'disabled', label: 'Disabled', tone: 'idle', count: disabled },
];

describe('fleetHeadline', () => {
  it('leads with what is down, out of the active datasources', () => {
    expect(fleetHeadline(segments(2, 1, 1, 1))).toBe('1 of 4 active datasources is down');
    expect(fleetHeadline(segments(0, 2, 0, 0))).toBe('2 of 2 active datasources are down');
  });

  it('says when everything active is up', () => {
    expect(fleetHeadline(segments(3, 0, 0, 1))).toBe('All 3 active datasources are up');
    expect(fleetHeadline(segments(1, 0, 0, 0))).toBe('The datasource is up');
  });

  it('covers nothing checked yet, some unchecked, and all disabled', () => {
    expect(fleetHeadline(segments(0, 0, 2, 0))).toBe('No datasource has been checked yet');
    expect(fleetHeadline(segments(2, 0, 1, 0))).toBe('2 up, 1 not checked yet');
    expect(fleetHeadline(segments(0, 0, 0, 2))).toBe('All datasources are disabled');
  });
});

describe('liveText', () => {
  it('says Live without a clock while pushed, and when it last updated while polling', () => {
    const now = Date.now();
    expect(liveText('live', 'stream', false, now, now)).toBe('Live');
    expect(liveText('live', 'polling', false, now - 120000, now)).toBe('Live · updated 2 min ago');
    expect(liveText('stale', 'stream', false, now - 120000, now)).toBe('Reconnecting · last update 2 min ago');
    expect(liveText('connecting', 'stream', true, null, now)).toBe('Not connected · retrying');
  });
});

@Component({ template: '' })
class Blank {}

describe('DatasourcesComponent', () => {
  let fixture: ComponentFixture<DatasourcesComponent>;
  const live = {
    datasources: signal<DatasourceHealth[] | null>(null),
    healthCheckEnabled: signal(true),
    status: signal('connecting'),
    transport: signal<'stream' | 'polling'>('stream'),
    failed: signal(false),
    updatedAt: signal<number | null>(null),
    pause: vi.fn(), resume: vi.fn(), refresh: vi.fn(),
  };

  beforeEach(async () => {
    live.datasources.set(null);
    live.failed.set(false);
    live.healthCheckEnabled.set(true);
    await TestBed.configureTestingModule({
      imports: [DatasourcesComponent],
      providers: [provideRouter([{ path: 'datasources', component: Blank }])],
    }).overrideComponent(DatasourcesComponent, {
      set: { providers: [{ provide: DatasourceLiveService, useValue: live }] },
    }).compileComponents();
    await TestBed.inject(Router).navigateByUrl('/datasources');
    fixture = TestBed.createComponent(DatasourcesComponent);
    fixture.detectChanges();
  });

  const text = () => fixture.nativeElement.textContent as string;
  const cards = () => fixture.nativeElement.querySelectorAll('.ds-card:not(.ds-card--skeleton)') as NodeListOf<HTMLElement>;

  it('shows skeleton cards until the first snapshot', () => {
    expect(fixture.nativeElement.querySelectorAll('.ds-card--skeleton').length).toBe(3);
  });

  it('shows a card per datasource with its status and what needs attention', () => {
    live.datasources.set([
      ds('anzo-prod', 'UP', { recentChecks: [{ status: 'UP', responseTimeMs: 80, errorMessage: null, checkTime: '2026-11-28T12:00:00Z' }] }),
      ds('anzo-staging', 'DOWN', { lastHealthError: 'Connection refused', consecutiveFailures: 3, routeCount: 4 }),
    ]);
    fixture.detectChanges();

    expect(cards().length).toBe(2);
    expect(text()).toContain('1 of 2 active datasources is down');
    expect(cards()[1].textContent).toContain('Connection refused');
    expect(cards()[1].textContent).toContain('3 failed checks in a row');
    expect(cards()[1].textContent).toContain('4 routes');
    expect(cards()[0].querySelector('a.ds-card__link')!.getAttribute('href')).toContain('config-datasource/anzo-prod');
  });

  it('filters by status and by name', () => {
    live.datasources.set([ds('alpha', 'UP'), ds('beta', 'DOWN'), ds('zeta', 'UP')]);
    fixture.detectChanges();
    const component = fixture.componentInstance as any;

    component.group.set('up');
    fixture.detectChanges();
    expect(component.visible().map((d: DatasourceHealth) => d.dataSourceId)).toEqual(['alpha', 'zeta']);

    component.term.set('zet');
    fixture.detectChanges();
    expect(component.visible().map((d: DatasourceHealth) => d.dataSourceId)).toEqual(['zeta']);
  });

  it('explains an empty list', () => {
    live.datasources.set([]);
    fixture.detectChanges();
    expect(text()).toContain('No datasources yet');
  });

  it('says when it cannot load, and when automated checks are off', () => {
    live.failed.set(true);
    live.healthCheckEnabled.set(false);
    fixture.detectChanges();
    expect(text()).toContain("Couldn't load datasources");
    expect(text()).toContain('Automated health checks are off');
  });
});
