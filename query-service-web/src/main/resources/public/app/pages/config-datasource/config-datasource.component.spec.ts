import type { MockedObject } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, Subject } from 'rxjs';
import { signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';

import { ConfigDatasourceComponent } from './config-datasource.component';
import { DatasourceService } from '../../core/services/datasource.service';
import { DatasourceLiveService } from '../../core/services/datasource-live.service';
import { EVENT_STREAM_FETCH } from '../../core/services/live-connection';
import { MetricsService } from '../../core/services/metrics.service';
import { Datasources } from '../../core/models/datasources';
import { DatasourceHealth } from '../../core/models/datasource-health';
import { SaveDatasourceDialogData } from '../config-datasource-modal/config-datasource-modal.component';
import { QuickActionsService } from '../../shared/quick-actions/quick-actions.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';

describe('ConfigDatasourceComponent', () => {
  let component: ConfigDatasourceComponent;
  let fixture: ComponentFixture<ConfigDatasourceComponent>;
  let datasourceService: MockedObject<DatasourceService>;
  let dialog: { open: ReturnType<typeof vi.fn> };
  let afterClosed: Subject<boolean>;
  let live: ReturnType<typeof fakeLive>;
  // The component's template-facing members are protected.
  const c = () => component as any;

  // As returned by GET /api/datasources/{id}: no password
  const stored = {
    dataSourceId: 'ds1',
    url: 'http://anzo:8080',
    timeOutSeconds: '30',
    maxQueryHeaderLength: '8192',
    username: 'mock-user',
    validateCertificate: true,
    camelRouteTemplateNames: ['orders', 'customers'],
    status: 'UP',
  } as unknown as Datasources;

  const health: DatasourceHealth = {
    dataSourceId: 'ds1', url: 'http://anzo:8080', status: 'DOWN', lastHealthCheck: new Date().toISOString(),
    lastHealthError: 'Connection refused', consecutiveFailures: 3, routeCount: 2,
    recentChecks: [{ status: 'DOWN', responseTimeMs: null, errorMessage: 'Connection refused', checkTime: new Date().toISOString() }],
  };

  function fakeLive() {
    return {
      datasources: signal<DatasourceHealth[] | null>([health]),
      healthCheckEnabled: signal(true),
      status: signal('live'),
      transport: signal<'stream' | 'polling'>('stream'),
      failed: signal(false),
      updatedAt: signal<number | null>(null),
      patch: vi.fn(),
      pause: vi.fn(),
      resume: vi.fn(),
      refresh: vi.fn(),
    };
  }

  beforeEach(async () => {
    datasourceService = {
      getDatasource: vi.fn().mockReturnValue(of(stored)),
      testDatasource: vi.fn().mockReturnValue(of({ status: 'success' })),
      triggerHealthCheck: vi.fn().mockReturnValue(of({ status: 'UP' })),
      enableDatasource: vi.fn().mockReturnValue(of({})),
      disableDatasource: vi.fn().mockReturnValue(of({})),
    } as unknown as MockedObject<DatasourceService>;
    afterClosed = new Subject<boolean>();
    dialog = { open: vi.fn().mockReturnValue({ afterClosed: () => afterClosed }) };
    live = fakeLive();
    const metricsService = {
      getDatasourceRouteMetrics: vi.fn().mockReturnValue(of({ metrics: [] })),
      getRouteMetrics: vi.fn().mockReturnValue(of({ metrics: [] })),
      getPersistedMetrics: vi.fn().mockReturnValue(of({ metrics: [] })),
    };

    await TestBed.configureTestingModule({
      imports: [ConfigDatasourceComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ dataSourceId: 'ds1' })) } },
        { provide: DatasourceService, useValue: datasourceService },
        { provide: DatasourceLiveService, useValue: live },
        { provide: MetricsService, useValue: metricsService },
        { provide: MatDialog, useValue: dialog },
        { provide: EVENT_STREAM_FETCH, useValue: () => Promise.resolve(new Response('', { status: 404 })) },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ConfigDatasourceComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('shows the pushed health: status, how long it has been down, and the error', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('3 failed checks in a row');
    expect(text).toContain('Failing');
    expect(text).toContain('Connection refused');
    expect(c().status()).toBe('DOWN');
  });

  it('does not pre-fill the password and accepts the form without one', () => {
    expect(c().form.value.password).toBe('');
    expect(c().form.valid).toBe(true);
    expect(c().changes()).toEqual([]);
  });

  it('confirms a save with the changes and the routes that use the datasource; no password means keep it', () => {
    c().form.controls.timeOutSeconds.setValue('60');
    c().save();

    const data = vi.mocked(dialog.open).mock.lastCall![1].data as SaveDatasourceDialogData;
    expect(data.datasource.password).toBeUndefined();
    expect(data.datasource.timeOutSeconds).toBe('60');
    expect(data.changes).toEqual([{ label: 'Timeout', from: '30', to: '60' }]);
    expect(data.associatedRoutes).toEqual(['orders', 'customers']);
  });

  it('lists a newly entered password as changed without showing it', () => {
    c().form.controls.password.setValue('new-password');
    c().save();

    const data = vi.mocked(dialog.open).mock.lastCall![1].data as SaveDatasourceDialogData;
    expect(data.datasource.password).toBe('new-password');
    expect(data.changes).toEqual([{ label: 'Password', from: null, to: null }]);
  });

  it('does not open the confirmation without changes', () => {
    c().save();
    expect(dialog.open).not.toHaveBeenCalled();
  });

  it('requires the password when the URL changes', () => {
    c().form.controls.url.setValue('https://other-anzo:8443');

    expect(c().form.controls.password.hasError('requiredForChange')).toBe(true);
    expect(c().form.valid).toBe(false);
    c().save();
    expect(dialog.open).not.toHaveBeenCalled();

    c().form.controls.password.setValue('new-password');
    expect(c().form.valid).toBe(true);
  });

  it('requires the password when the username changes, and not after changing it back', () => {
    c().form.controls.username.setValue('someone-else');
    expect(c().form.valid).toBe(false);

    c().form.controls.username.setValue('mock-user');
    expect(c().form.valid).toBe(true);
  });

  it('rejects a timeout that is not a whole number', () => {
    c().form.controls.timeOutSeconds.setValue('30s');
    expect(c().form.valid).toBe(false);
  });

  it('after saving, reloads the settings so the form is clean again', () => {
    c().form.controls.timeOutSeconds.setValue('60');
    c().save();
    afterClosed.next(true);

    expect(datasourceService.getDatasource).toHaveBeenCalledTimes(2);
    expect(c().changes()).toEqual([]);
  });

  it('tests the connection without a password so the server uses the stored one, and shows the result', () => {
    c().runTest();

    const sent = vi.mocked(datasourceService.testDatasource).mock.lastCall![0];
    expect(sent.password).toBeUndefined();
    expect(sent.dataSourceId).toBe('ds1');
    expect(c().test().state).toBe('success');
  });

  it('shows the reason a connection test failed', () => {
    datasourceService.testDatasource.mockReturnValue(of({ status: 'error', message: 'Unauthorized' }));
    c().runTest();
    expect(c().test()).toEqual({ state: 'error', message: 'Unauthorized' });
  });

  it('shows "checking" at once when a check is run, then the result', () => {
    c().checkNow();
    expect(live.patch).toHaveBeenNthCalledWith(1, 'ds1', { status: 'CHECKING' });
    expect(live.patch).toHaveBeenNthCalledWith(2, 'ds1', { status: 'UP', lastHealthError: null });
    expect(c().busy()).toBeNull();
  });

  it('disables the datasource and shows it disabled before the server confirms', () => {
    c().setEnabled(false);
    expect(live.patch).toHaveBeenCalledWith('ds1', { status: 'DISABLED' });
    expect(datasourceService.disableDatasource).toHaveBeenCalledWith('ds1');
  });

  describe('quick actions bar', () => {
    const actions = () => TestBed.inject(QuickActionsService).actions().filter(a => a.group === 'page');
    const shown = () => actions().filter(a => !a.visible || a.visible()).map(a => a.label);
    const action = (id: string) => actions().find(a => a.id === id)!;

    it('adds the page\'s actions while it is open, and removes them when it closes', () => {
      expect(shown()).toEqual(['Save datasource', 'Test connection', 'Run health check', 'Disable datasource',
        'Delete datasource', 'Show usage']);
      fixture.destroy();
      expect(actions()).toEqual([]);
    });

    it('says why an action cannot run, as the buttons do', () => {
      expect(action('datasource:save').disabledReason!()).toBe('Nothing has changed');
      c().form.controls.timeOutSeconds.setValue('ten');
      expect(action('datasource:save').disabledReason!()).toBe('Some connection settings are invalid');
      live.healthCheckEnabled.set(false);
      expect(action('datasource:health').disabledReason!()).toBe('Automated health checks are off on the server');
    });

    it('asks before disabling, naming how many routes stop, and disables only when confirmed', () => {
      action('datasource:disable').run();
      const [type, config] = dialog.open.mock.lastCall as any[];
      expect(type).toBe(ConfirmDialogComponent);
      expect(config.data.message[0]).toBe('All 2 routes using it stop answering queries until it is enabled again.');
      expect(datasourceService.disableDatasource).not.toHaveBeenCalled();
      afterClosed.next(true);
      expect(datasourceService.disableDatasource).toHaveBeenCalledWith('ds1');
    });
  });
});
