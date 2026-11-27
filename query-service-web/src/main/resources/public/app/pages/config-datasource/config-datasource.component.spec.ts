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

describe('ConfigDatasourceComponent', () => {
  let component: ConfigDatasourceComponent;
  let fixture: ComponentFixture<ConfigDatasourceComponent>;
  let datasourceService: jasmine.SpyObj<DatasourceService>;
  let dialog: jasmine.SpyObj<MatDialog>;

  // As returned by GET /api/datasources/{id}: no password
  const stored = {
    dataSourceId: 'ds1',
    url: 'http://anzo:8080',
    timeOutSeconds: '30',
    maxQueryHeaderLength: '8192',
    username: 'mock-user',
    validateCertificate: true,
    camelRouteTemplate: [],
    status: 'UP'
  } as unknown as Datasources;

  beforeEach(async () => {
    datasourceService = jasmine.createSpyObj('DatasourceService',
      ['getDatasource', 'getHealthCheckConfig', 'testDatasource', 'modifyDatasource']);
    datasourceService.getDatasource.and.returnValue(of(stored));
    datasourceService.getHealthCheckConfig.and.returnValue(of({enabled: true, available: true}));
    datasourceService.testDatasource.and.returnValue(of({status: 'success'}));
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    const metricsService = jasmine.createSpyObj('MetricsService', ['getDatasourceRouteMetrics']);
    metricsService.getDatasourceRouteMetrics.and.returnValue(of({metrics: []}));

    await TestBed.configureTestingModule({
      declarations: [ConfigDatasourceComponent],
      imports: [HttpClientTestingModule],
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
    expect(component.configDatasource.valid).toBeTrue();
  });

  it('saves without a password so the stored one is kept', () => {
    component.config('ds1');

    const sent = (dialog.open.calls.mostRecent().args[1] as any).data.datasource as Datasources;
    expect(sent.password).toBeUndefined();
    expect(sent.url).toBe('http://anzo:8080');
  });

  it('sends a newly entered password', () => {
    component.configDatasource.controls['password'].setValue('new-password');
    component.config('ds1');

    const sent = (dialog.open.calls.mostRecent().args[1] as any).data.datasource as Datasources;
    expect(sent.password).toBe('new-password');
  });

  it('requires the password when the URL changes', () => {
    component.configDatasource.controls['url'].setValue('https://other-anzo:8443');

    expect(component.password?.hasError('requiredForChange')).toBeTrue();
    expect(component.configDatasource.valid).toBeFalse();
    component.config('ds1');
    expect(dialog.open).not.toHaveBeenCalled();

    component.configDatasource.controls['password'].setValue('new-password');
    expect(component.configDatasource.valid).toBeTrue();
  });

  it('requires the password when the username changes, and not after changing it back', () => {
    component.configDatasource.controls['username'].setValue('someone-else');
    expect(component.configDatasource.valid).toBeFalse();

    component.configDatasource.controls['username'].setValue('mock-user');
    expect(component.configDatasource.valid).toBeTrue();
  });

  it('tests the connection without a password so the server uses the stored one', () => {
    component.test();

    const sent = datasourceService.testDatasource.calls.mostRecent().args[0];
    expect(sent.password).toBeUndefined();
    expect(sent.dataSourceId).toBe('ds1');
  });
});
