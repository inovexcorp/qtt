import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { SettingsService } from './settings.service';
import { CacheStatistics } from '../models/ontology/cache-statistics';

describe('SettingsService', () => {
  let service: SettingsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(SettingsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getDatabaseInfo', () => {
    it('should retrieve database system information', () => {
      const mockDatabaseInfo = {
        version: '1.0.0',
        uptime: 3600000,
        memory: { total: 8192, free: 4096 }
      };

      service.getDatabaseInfo().subscribe(info => {
        expect(info).toEqual(mockDatabaseInfo);
        expect(info.version).toBe('1.0.0');
      });

      const req = httpMock.expectOne('/queryrest/api/settings/sysinfo');
      expect(req.request.method).toBe('GET');
      req.flush(mockDatabaseInfo);
    });

    it('should handle errors when getting database info', () => {
      service.getDatabaseInfo().subscribe(
        () => fail('should have failed'),
        error => {
          expect(error).toBeTruthy();
        }
      );

      const req = httpMock.expectOne('/queryrest/api/settings/sysinfo');
      req.error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });
    });
  });

  describe('getStats', () => {
    it('should retrieve statistics', () => {
      const mockStats = {
        totalRequests: 1000,
        averageResponseTime: 250,
        errorRate: 0.01
      };

      service.getStats().subscribe(stats => {
        expect(stats).toEqual(mockStats);
        expect(stats.totalRequests).toBe(1000);
      });

      const req = httpMock.expectOne('/queryrest/api/settings/stats');
      expect(req.request.method).toBe('GET');
      req.flush(mockStats);
    });

    it('should handle errors when getting stats', () => {
      service.getStats().subscribe(
        () => fail('should have failed'),
        error => {
          expect(error).toBeTruthy();
        }
      );

      const req = httpMock.expectOne('/queryrest/api/settings/stats');
      req.error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });
    });
  });

  describe('getCacheStatistics', () => {
    it('should retrieve cache statistics', () => {
      const mockCacheStats: CacheStatistics = {
        totalRoutes: 10,
        totalElements: 5000,
        cacheHits: 1000,
        cacheMisses: 100,
        hitRate: 0.91
      };

      service.getCacheStatistics().subscribe(stats => {
        expect(stats).toEqual(mockCacheStats);
        expect(stats.totalRoutes).toBe(10);
        expect(stats.hitRate).toBe(0.91);
      });

      const req = httpMock.expectOne('/queryrest/api/ontology/cache/statistics');
      expect(req.request.method).toBe('GET');
      req.flush(mockCacheStats);
    });

    it('should handle errors when getting cache statistics', () => {
      service.getCacheStatistics().subscribe(
        () => fail('should have failed'),
        error => {
          expect(error).toBeTruthy();
        }
      );

      const req = httpMock.expectOne('/queryrest/api/ontology/cache/statistics');
      req.error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });
    });
  });
});
