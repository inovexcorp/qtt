import type { MockedObject } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';

import { ConfigDatasourceComponent } from './config-datasource.component';
import { DatasourceService } from '../../core/services/datasource.service';
import { MetricsService } from '../../core/services/metrics.service';
import { Datasources } from '../../core/models/datasources';
import { QuickActionsService } from '../../shared/quick-actions/quick-actions.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';

describe('ConfigDatasourceComponent', () => {
  let component: ConfigDatasourceComponent;
  let fixture: ComponentFixture<ConfigDatasourceComponent>;
  let datasourceService: MockedObject<DatasourceService>;
  let dialog: MockedObject<MatDialog>;

  // As returned by GET /api/datasources/{id}: no password
  const stored = {
    dataSourceId: 'ds1',
    url: 'http://anzo:8080',
    timeOutSeconds: '30',
    maxQueryHeaderLength: '8192',
    username: 'mock-user',
    validateCertificate: true,
    camelRouteTemplateNames: [],
    status: 'UP'
  } as unknown as Datasources;

  beforeEach(async () => {
    datasourceService = {
      getDatasource: vi.fn().mockName('DatasourceService.getDatasource'),
      getHealthCheckConfig: vi.fn().mockName('DatasourceService.getHealthCheckConfig'),
      testDatasource: vi.fn().mockName('DatasourceService.testDatasource'),
      disableDatasource: vi.fn().mockName('DatasourceService.disableDatasource'),
      modifyDatasource: vi.fn().mockName('DatasourceService.modifyDatasource')
    } as unknown as MockedObject<DatasourceService>;
    datasourceService.getDatasource.mockReturnValue(of(stored));
    datasourceService.getHealthCheckConfig.mockReturnValue(of({ enabled: true, available: true }));
    datasourceService.testDatasource.mockReturnValue(of({ status: 'success' }));
    dialog = {
      open: vi.fn().mockName('MatDialog.open')
    } as unknown as MockedObject<MatDialog>;
    const metricsService = {
      getDatasourceRouteMetrics: vi.fn().mockName('MetricsService.getDatasourceRouteMetrics')
    };
    metricsService.getDatasourceRouteMetrics.mockReturnValue(of({ metrics: [] }));

    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, ConfigDatasourceComponent],
      providers: [
          { provide: ActivatedRoute, useValue: { params: of({ dataSourceId: 'ds1' }), snapshot: { params: { dataSourceId: 'ds1' } } } },
          { provide: DatasourceService, useValue: datasourceService },
          { provide: MetricsService, useValue: metricsService },
          { provide: MatDialog, useValue: dialog }
      ],
      schemas: [NO_ERRORS_SCHEMA]
    })
      .compileComponents();

    fixture = TestBed.createComponent(ConfigDatasourceComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('does not pre-fill the password and accepts the form without one', () => {
    expect(component.configDatasource.value['password']).toBe('');
    expect(component.configDatasource.valid).toBe(true);
  });

  it('saves without a password so the stored one is kept', () => {
    component.config('ds1');

    const sent = (vi.mocked(dialog.open).mock.lastCall![1] as any).data.datasource as Datasources;
    expect(sent.password).toBeUndefined();
    expect(sent.url).toBe('http://anzo:8080');
  });

  it('sends a newly entered password', () => {
    component.configDatasource.controls['password'].setValue('new-password');
    component.config('ds1');

    const sent = (vi.mocked(dialog.open).mock.lastCall![1] as any).data.datasource as Datasources;
    expect(sent.password).toBe('new-password');
  });

  it('requires the password when the URL changes', () => {
    component.configDatasource.controls['url'].setValue('https://other-anzo:8443');

    expect(component.password?.hasError('requiredForChange')).toBe(true);
    expect(component.configDatasource.valid).toBe(false);
    component.config('ds1');
    expect(dialog.open).not.toHaveBeenCalled();

    component.configDatasource.controls['password'].setValue('new-password');
    expect(component.configDatasource.valid).toBe(true);
  });

  it('requires the password when the username changes, and not after changing it back', () => {
    component.configDatasource.controls['username'].setValue('someone-else');
    expect(component.configDatasource.valid).toBe(false);

    component.configDatasource.controls['username'].setValue('mock-user');
    expect(component.configDatasource.valid).toBe(true);
  });

  it('tests the connection without a password so the server uses the stored one', () => {
    component.test();

    const sent = vi.mocked(datasourceService.testDatasource).mock.lastCall![0];
    expect(sent.password).toBeUndefined();
    expect(sent.dataSourceId).toBe('ds1');
  });

  it('asks before disabling, and disables only when confirmed', () => {
    datasourceService.disableDatasource.mockReturnValue(of({}));
    (component as any).associatedRoutes = ['r1', 'r2'];
    dialog.open.mockReturnValue({ afterClosed: () => of(false) } as any);
    component.disableDatasource();
    const [dialogType, config] = vi.mocked(dialog.open).mock.lastCall! as any[];
    expect(dialogType).toBe(ConfirmDialogComponent);
    expect(config.data.title).toBe('Disable ds1?');
    expect(config.data.message[0]).toContain('All 2 routes');
    expect(datasourceService.disableDatasource).not.toHaveBeenCalled();

    dialog.open.mockReturnValue({ afterClosed: () => of(true) } as any);
    component.disableDatasource();
    expect(datasourceService.disableDatasource).toHaveBeenCalledWith('ds1');
  });

  it('adds its actions to the quick actions bar while open, by role and state', () => {
    const labels = () => TestBed.inject(QuickActionsService).actions()
      .filter(a => a.group === 'page' && (!a.visible || a.visible())).map(a => a.label);
    // Status UP: enable is not offered.
    expect(labels()).toEqual(['Save datasource', 'Test connection', 'Run health check', 'Disable datasource',
      'Delete datasource', 'Show usage']);
    component.datasourceStatus = 'DISABLED';
    expect(labels()).toContain('Enable datasource');
    expect(labels()).not.toContain('Disable datasource');
    const health = TestBed.inject(QuickActionsService).actions().find(a => a.id === 'datasource:health')!;
    expect(health.disabledReason!()).toBe('The datasource is disabled');
    fixture.destroy();
    expect(TestBed.inject(QuickActionsService).actions().filter(a => a.group === 'page')).toEqual([]);
  });
});
