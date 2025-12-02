import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { MetricsService } from './metrics.service';
import { RouteMetricsResponse } from '../models/metric-data';

describe('MetricsService', () => {
  let service: MetricsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(MetricsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getRouteMetrics', () => {
    it('should retrieve route metrics', () => {
      const mockMetrics: RouteMetricsResponse = {
        routes: [
          { routeId: 'route-1', requestCount: 100, averageResponseTime: 200 }
        ]
      } as RouteMetricsResponse;

      service.getRouteMetrics().subscribe(metrics => {
        expect(metrics).toEqual(mockMetrics);
        expect(metrics.routes).toBeDefined();
      });

      const req = httpMock.expectOne('/queryrest/api/metrics/routes/');
      expect(req.request.method).toBe('GET');
      req.flush(mockMetrics);
    });

    it('should handle errors when getting route metrics', () => {
      service.getRouteMetrics().subscribe(
        () => fail('should have failed'),
        error => {
          expect(error).toBeTruthy();
        }
      );

      const req = httpMock.expectOne('/queryrest/api/metrics/routes/');
      req.error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });
    });
  });

  describe('getPersistedMetrics', () => {
    it('should retrieve persisted metrics', () => {
      const mockMetrics: RouteMetricsResponse = {
        routes: [
          { routeId: 'route-1', requestCount: 500, averageResponseTime: 150 }
        ]
      } as RouteMetricsResponse;

      service.getPersistedMetrics().subscribe(metrics => {
        expect(metrics).toEqual(mockMetrics);
      });

      const req = httpMock.expectOne('/queryrest/api/metrics/route/persisted');
      expect(req.request.method).toBe('GET');
      req.flush(mockMetrics);
    });

    it('should handle errors when getting persisted metrics', () => {
      service.getPersistedMetrics().subscribe(
        () => fail('should have failed'),
        error => {
          expect(error).toBeTruthy();
        }
      );

      const req = httpMock.expectOne('/queryrest/api/metrics/route/persisted');
      req.error(new ProgressEvent('error'), { status: 404, statusText: 'Not Found' });
    });
  });

  describe('getDatasourceRouteMetrics', () => {
    it('should retrieve metrics for a specific datasource', () => {
      const dataSource = 'datasource-123';
      const mockMetrics: RouteMetricsResponse = {
        routes: [
          { routeId: 'route-1', requestCount: 75, averageResponseTime: 180 }
        ]
      } as RouteMetricsResponse;

      service.getDatasourceRouteMetrics(dataSource).subscribe(metrics => {
        expect(metrics).toEqual(mockMetrics);
      });

      const req = httpMock.expectOne('/queryrest/api/metrics/routes/datasource-123');
      expect(req.request.method).toBe('GET');
      req.flush(mockMetrics);
    });

    it('should handle errors when getting datasource metrics', () => {
      const dataSource = 'datasource-123';

      service.getDatasourceRouteMetrics(dataSource).subscribe(
        () => fail('should have failed'),
        error => {
          expect(error).toBeTruthy();
        }
      );

      const req = httpMock.expectOne('/queryrest/api/metrics/routes/datasource-123');
      req.error(new ProgressEvent('error'), { status: 404, statusText: 'Not Found' });
    });
  });
});
