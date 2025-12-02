import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { DatasourceService } from './datasource.service';
import { Datasources } from '../models/datasources';

describe('DatasourceService', () => {
  let service: DatasourceService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(DatasourceService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getDatasources', () => {
    it('should retrieve all datasources', () => {
      const mockDatasources: Datasources[] = [
        {
          camelRouteTemplate: [],
          dataSourceId: '1',
          timeOutSeconds: '30',
          maxQueryHeaderLength: '1000',
          username: 'user1',
          password: 'pass1',
          url: 'http://example.com/sparql',
          validateCertificate: true
        } as Datasources,
        {
          camelRouteTemplate: [],
          dataSourceId: '2',
          timeOutSeconds: '30',
          maxQueryHeaderLength: '1000',
          username: 'user2',
          password: 'pass2',
          url: 'http://example.com/sparql2',
          validateCertificate: true
        } as Datasources
      ];

      service.getDatasources().subscribe(datasources => {
        expect(datasources.length).toBe(2);
        expect(datasources[0].dataSourceId).toBe('1');
        expect(datasources[1].dataSourceId).toBe('2');
      });

      const req = httpMock.expectOne('/queryrest/api/datasources/');
      expect(req.request.method).toBe('GET');
      req.flush(mockDatasources);
    });
  });

  describe('getDatasource', () => {
    it('should retrieve a specific datasource by ID', () => {
      const mockDatasource: Datasources = {
        camelRouteTemplate: [],
        dataSourceId: '123',
        timeOutSeconds: '30',
        maxQueryHeaderLength: '1000',
        username: 'testuser',
        password: 'testpass',
        url: 'http://example.com/sparql',
        validateCertificate: true
      } as Datasources;

      service.getDatasource('123').subscribe(datasource => {
        expect(datasource.dataSourceId).toBe('123');
        expect(datasource.url).toBe('http://example.com/sparql');
      });

      const req = httpMock.expectOne('/queryrest/api/datasources/123');
      expect(req.request.method).toBe('GET');
      req.flush(mockDatasource);
    });
  });

  describe('modifyDatasource', () => {
    it('should modify a datasource', () => {
      const datasource: Datasources = {
        camelRouteTemplate: [],
        dataSourceId: '123',
        timeOutSeconds: '30',
        maxQueryHeaderLength: '1000',
        username: 'testuser',
        password: 'testpass',
        url: 'http://example.com/sparql',
        validateCertificate: true
      } as Datasources;

      service.modifyDatasource(datasource).subscribe(response => {
        expect(response).toBeTruthy();
      });

      const req = httpMock.expectOne('/queryrest/api/datasources/');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(datasource);
      req.flush([datasource]);
    });
  });

  describe('deleteDatasource', () => {
    it('should delete a datasource by ID', () => {
      const dataSourceId = '123';

      service.deleteDatasource(dataSourceId).subscribe(response => {
        expect(response).toBeTruthy();
      });

      const req = httpMock.expectOne('/queryrest/api/datasources/123');
      expect(req.request.method).toBe('DELETE');
      req.flush({ success: true });
    });
  });

  describe('testDatasource', () => {
    it('should test a datasource connection', () => {
      const datasource: Datasources = {
        camelRouteTemplate: [],
        dataSourceId: '123',
        timeOutSeconds: '30',
        maxQueryHeaderLength: '1000',
        username: 'testuser',
        password: 'testpass',
        url: 'http://example.com/sparql',
        validateCertificate: true
      } as Datasources;

      service.testDatasource(datasource).subscribe(response => {
        expect(response.success).toBe(true);
      });

      const req = httpMock.expectOne('/queryrest/api/datasources/test');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(datasource);
      req.flush({ success: true, message: 'Connection successful' });
    });
  });

  describe('triggerHealthCheck', () => {
    it('should trigger manual health check for a datasource', () => {
      const dataSourceId = '123';

      service.triggerHealthCheck(dataSourceId).subscribe(response => {
        expect(response.status).toBe('healthy');
      });

      const req = httpMock.expectOne('/queryrest/api/datasources/123/healthcheck');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush({ status: 'healthy' });
    });
  });

  describe('getDatasourceHealth', () => {
    it('should get health status and history for a datasource', () => {
      const dataSourceId = '123';
      const mockHealth = {
        status: 'healthy',
        lastChecked: new Date().toISOString(),
        history: []
      };

      service.getDatasourceHealth(dataSourceId).subscribe(health => {
        expect(health.status).toBe('healthy');
      });

      const req = httpMock.expectOne('/queryrest/api/datasources/123/health');
      expect(req.request.method).toBe('GET');
      req.flush(mockHealth);
    });
  });

  describe('getHealthSummary', () => {
    it('should get health summary for all datasources', () => {
      const mockSummary = {
        totalDatasources: 5,
        healthy: 4,
        unhealthy: 1
      };

      service.getHealthSummary().subscribe(summary => {
        expect(summary.totalDatasources).toBe(5);
        expect(summary.healthy).toBe(4);
      });

      const req = httpMock.expectOne('/queryrest/api/datasources/health/summary');
      expect(req.request.method).toBe('GET');
      req.flush(mockSummary);
    });
  });

  describe('disableDatasource', () => {
    it('should disable a datasource', () => {
      const dataSourceId = '123';

      service.disableDatasource(dataSourceId).subscribe(response => {
        expect(response.disabled).toBe(true);
      });

      const req = httpMock.expectOne('/queryrest/api/datasources/123/disable');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({});
      req.flush({ disabled: true });
    });
  });

  describe('enableDatasource', () => {
    it('should enable a datasource', () => {
      const dataSourceId = '123';

      service.enableDatasource(dataSourceId).subscribe(response => {
        expect(response.enabled).toBe(true);
      });

      const req = httpMock.expectOne('/queryrest/api/datasources/123/enable');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({});
      req.flush({ enabled: true });
    });
  });
});
