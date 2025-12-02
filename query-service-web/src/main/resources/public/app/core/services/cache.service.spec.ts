import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { CacheService } from './cache.service';
import { CacheInfo, RouteCacheStats } from '../models/cache-info';

describe('CacheService', () => {
  let service: CacheService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [CacheService]
    });
    service = TestBed.inject(CacheService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getCacheInfo', () => {
    it('should return cache connection information', () => {
      const mockCacheInfo: CacheInfo = {
        enabled: true,
        connected: true,
        type: 'redis',
        host: 'localhost',
        port: 6379,
        database: 0,
        keyPrefix: 'qtt:cache:',
        defaultTtlSeconds: 3600,
        compressionEnabled: true,
        failOpen: true
      };

      service.getCacheInfo().subscribe(info => {
        expect(info).toEqual(mockCacheInfo);
        expect(info.enabled).toBe(true);
        expect(info.connected).toBe(true);
      });

      const req = httpMock.expectOne('/queryrest/api/routes/cache/info');
      expect(req.request.method).toBe('GET');
      req.flush(mockCacheInfo);
    });

    it('should handle cache info request errors', () => {
      service.getCacheInfo().subscribe(
        () => fail('should have failed'),
        error => {
          expect(error).toBeTruthy();
        }
      );

      const req = httpMock.expectOne('/queryrest/api/routes/cache/info');
      req.error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });
    });
  });

  describe('clearRouteCache', () => {
    it('should clear cache for a specific route', () => {
      const routeId = 'test-route-123';

      service.clearRouteCache(routeId).subscribe(response => {
        expect(response).toBeTruthy();
      });

      const req = httpMock.expectOne(`/queryrest/api/routes/${routeId}/cache`);
      expect(req.request.method).toBe('DELETE');
      req.flush({ success: true });
    });

    it('should handle errors when clearing route cache', () => {
      const routeId = 'test-route-123';

      service.clearRouteCache(routeId).subscribe(
        () => fail('should have failed'),
        error => {
          expect(error).toBeTruthy();
        }
      );

      const req = httpMock.expectOne(`/queryrest/api/routes/${routeId}/cache`);
      req.error(new ProgressEvent('error'), { status: 404, statusText: 'Not Found' });
    });
  });

  describe('clearAllCache', () => {
    it('should clear all cache entries', () => {
      service.clearAllCache().subscribe(response => {
        expect(response).toBeTruthy();
      });

      const req = httpMock.expectOne('/queryrest/api/routes/cache/all');
      expect(req.request.method).toBe('DELETE');
      req.flush({ success: true, cleared: 100 });
    });

    it('should handle errors when clearing all cache', () => {
      service.clearAllCache().subscribe(
        () => fail('should have failed'),
        error => {
          expect(error).toBeTruthy();
        }
      );

      const req = httpMock.expectOne('/queryrest/api/routes/cache/all');
      req.error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });
    });
  });

  describe('getRouteCacheStats', () => {
    it('should return cache statistics for a specific route', () => {
      const routeId = 'test-route-123';
      const mockStats: RouteCacheStats = {
        routeId: routeId,
        cacheEnabled: true,
        cacheTtlSeconds: 3600,
        routeKeyCount: 42,
        globalStats: {
          hits: 150,
          misses: 50,
          errors: 0,
          evictions: 5,
          keyCount: 1000,
          memoryUsageBytes: 5242880
        }
      };

      service.getRouteCacheStats(routeId).subscribe(stats => {
        expect(stats).toEqual(mockStats);
        expect(stats.globalStats.hits).toBe(150);
        expect(stats.globalStats.misses).toBe(50);
        expect(stats.routeKeyCount).toBe(42);
      });

      const req = httpMock.expectOne(`/queryrest/api/routes/${routeId}/cache/stats`);
      expect(req.request.method).toBe('GET');
      req.flush(mockStats);
    });

    it('should handle errors when getting route cache stats', () => {
      const routeId = 'test-route-123';

      service.getRouteCacheStats(routeId).subscribe(
        () => fail('should have failed'),
        error => {
          expect(error).toBeTruthy();
        }
      );

      const req = httpMock.expectOne(`/queryrest/api/routes/${routeId}/cache/stats`);
      req.error(new ProgressEvent('error'), { status: 404, statusText: 'Not Found' });
    });
  });
});
