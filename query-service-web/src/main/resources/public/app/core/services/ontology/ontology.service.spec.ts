import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { OntologyService } from './ontology.service';
import {
  OntologyElement,
  OntologyMetadata,
  CacheStatistics
} from '../../models/ontology/ontology-element';

describe('OntologyService', () => {
  let service: OntologyService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [OntologyService]
    });
    service = TestBed.inject(OntologyService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getOntologyElements', () => {
    it('should fetch ontology elements for a route', () => {
      const routeId = 'test-route';
      const mockElements: OntologyElement[] = [
        {
          uri: 'http://example.org/Class1',
          label: 'Class 1',
          type: 'class',
          description: 'A test class'
        },
        {
          uri: 'http://example.org/Property1',
          label: 'Property 1',
          type: 'objectProperty'
        }
      ];

      service.getOntologyElements({ routeId, type: 'all' }).subscribe(elements => {
        expect(elements.length).toBe(2);
        expect(elements[0].type).toBe('class');
        expect(elements[1].type).toBe('objectProperty');
      });

      const req = httpMock.expectOne('/queryrest/api/ontology/test-route');
      expect(req.request.method).toBe('GET');
      req.flush(mockElements);
    });

    it('should include type parameter when specified', () => {
      const routeId = 'test-route';
      const mockElements: OntologyElement[] = [
        { uri: 'http://example.org/Class1', label: 'Class 1', type: 'class' }
      ];

      service.getOntologyElements({ routeId, type: 'class' }).subscribe();

      const req = httpMock.expectOne(request =>
        request.url.includes('/queryrest/api/ontology/test-route') &&
        request.params.get('type') === 'class'
      );
      req.flush(mockElements);
    });

    it('should include prefix parameter when specified', () => {
      const routeId = 'test-route';
      const prefix = 'http://example.org';

      service.getOntologyElements({ routeId, type: 'all', prefix }).subscribe();

      const req = httpMock.expectOne(request =>
        request.url.includes('/queryrest/api/ontology/test-route') &&
        request.params.get('prefix') === prefix
      );
      req.flush([]);
    });

    it('should include limit parameter when specified', () => {
      const routeId = 'test-route';
      const limit = 100;

      service.getOntologyElements({ routeId, type: 'all', limit }).subscribe();

      const req = httpMock.expectOne(request =>
        request.url.includes('/queryrest/api/ontology/test-route') &&
        request.params.get('limit') === '100'
      );
      req.flush([]);
    });

    it('should cache results for queries without prefix', () => {
      const routeId = 'test-route';
      const mockElements: OntologyElement[] = [
        { uri: 'http://example.org/Class1', label: 'Class 1', type: 'class' }
      ];

      service.getOntologyElements({ routeId, type: 'all' }).subscribe();
      const req1 = httpMock.expectOne('/queryrest/api/ontology/test-route');
      req1.flush(mockElements);

      service.getOntologyElements({ routeId, type: 'all' }).subscribe();
      httpMock.expectNone('/queryrest/api/ontology/test-route');
    });

    it('should not cache results for queries with prefix', () => {
      const routeId = 'test-route';
      const prefix = 'http://example.org';

      service.getOntologyElements({ routeId, type: 'all', prefix }).subscribe();
      const req1 = httpMock.expectOne(request =>
        request.url.includes('/queryrest/api/ontology/test-route') &&
        request.params.get('prefix') === prefix
      );
      req1.flush([]);

      service.getOntologyElements({ routeId, type: 'all', prefix }).subscribe();
      const req2 = httpMock.expectOne(request =>
        request.url.includes('/queryrest/api/ontology/test-route') &&
        request.params.get('prefix') === prefix
      );
      req2.flush([]);
    });

    it('should return empty array on error', () => {
      const routeId = 'test-route';

      service.getOntologyElements({ routeId, type: 'all' }).subscribe(elements => {
        expect(elements).toEqual([]);
      });

      const req = httpMock.expectOne('/queryrest/api/ontology/test-route');
      req.error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });
    });
  });

  describe('searchOntologyElements', () => {
    it('should search ontology elements with prefix', () => {
      const routeId = 'test-route';
      const prefix = 'Person';
      const mockElements: OntologyElement[] = [
        { uri: 'http://example.org/Person', label: 'Person', type: 'class' }
      ];

      service.searchOntologyElements(routeId, prefix).subscribe(elements => {
        expect(elements.length).toBe(1);
        expect(elements[0].label).toBe('Person');
      });

      const req = httpMock.expectOne(request =>
        request.url.includes('/queryrest/api/ontology/test-route') &&
        request.params.get('prefix') === prefix &&
        request.params.get('limit') === '50'
      );
      req.flush(mockElements);
    });

    it('should use custom type and limit', () => {
      const routeId = 'test-route';
      const prefix = 'has';
      const type = 'objectProperty';
      const limit = 25;

      service.searchOntologyElements(routeId, prefix, type, limit).subscribe();

      const req = httpMock.expectOne(request =>
        request.url.includes('/queryrest/api/ontology/test-route') &&
        request.params.get('type') === type &&
        request.params.get('prefix') === prefix &&
        request.params.get('limit') === '25'
      );
      req.flush([]);
    });
  });

  describe('getOntologyMetadata', () => {
    it('should retrieve metadata about cached ontology', () => {
      const routeId = 'test-route';
      const mockMetadata: OntologyMetadata = {
        routeId: routeId,
        graphmartUri: 'http://example.org/graphmart',
        layerUris: 'http://example.org/layer1,http://example.org/layer2',
        elementCount: 500,
        lastUpdated: new Date().toISOString(),
        cached: true,
        status: 'VALID'
      };

      service.getOntologyMetadata(routeId).subscribe(metadata => {
        expect(metadata.routeId).toBe(routeId);
        expect(metadata.elementCount).toBe(500);
        expect(metadata.status).toBe('VALID');
      });

      const req = httpMock.expectOne(`/queryrest/api/ontology/${routeId}/metadata`);
      expect(req.request.method).toBe('GET');
      req.flush(mockMetadata);
    });

    it('should throw error on metadata fetch failure', () => {
      const routeId = 'test-route';

      service.getOntologyMetadata(routeId).subscribe(
        () => fail('should have failed'),
        error => {
          expect(error).toBeTruthy();
        }
      );

      const req = httpMock.expectOne(`/queryrest/api/ontology/${routeId}/metadata`);
      req.error(new ProgressEvent('error'), { status: 404, statusText: 'Not Found' });
    });
  });

  describe('refreshOntologyCache', () => {
    it('should refresh ontology cache for a route', () => {
      const routeId = 'test-route';

      service.refreshOntologyCache(routeId).subscribe(response => {
        expect(response).toBeTruthy();
      });

      const req = httpMock.expectOne(`/queryrest/api/ontology/${routeId}/refresh`);
      expect(req.request.method).toBe('POST');
      req.flush({ success: true });
    });

    it('should clear client cache when refreshing', () => {
      const routeId = 'test-route';
      const mockElements: OntologyElement[] = [
        { uri: 'http://example.org/Class1', label: 'Class 1', type: 'class' }
      ];

      service.getOntologyElements({ routeId, type: 'all' }).subscribe();
      const req1 = httpMock.expectOne(`/queryrest/api/ontology/${routeId}`);
      req1.flush(mockElements);

      service.refreshOntologyCache(routeId).subscribe();
      const refreshReq = httpMock.expectOne(`/queryrest/api/ontology/${routeId}/refresh`);
      refreshReq.flush({ success: true });

      service.getOntologyElements({ routeId, type: 'all' }).subscribe();
      const req2 = httpMock.expectOne(`/queryrest/api/ontology/${routeId}`);
      expect(req2.request.method).toBe('GET');
      req2.flush(mockElements);
    });
  });

  describe('clearOntologyCache', () => {
    it('should clear ontology cache for a route', () => {
      const routeId = 'test-route';

      service.clearOntologyCache(routeId).subscribe(response => {
        expect(response).toBeTruthy();
      });

      const req = httpMock.expectOne(`/queryrest/api/ontology/${routeId}`);
      expect(req.request.method).toBe('DELETE');
      req.flush({ success: true });
    });

    it('should clear client cache when clearing server cache', () => {
      const routeId = 'test-route';

      service.getOntologyElements({ routeId, type: 'all' }).subscribe();
      const req1 = httpMock.expectOne(`/queryrest/api/ontology/${routeId}`);
      req1.flush([]);

      service.clearOntologyCache(routeId).subscribe();
      const clearReq = httpMock.expectOne(`/queryrest/api/ontology/${routeId}`);
      clearReq.flush({ success: true });

      service.getOntologyElements({ routeId, type: 'all' }).subscribe();
      const req2 = httpMock.expectOne(`/queryrest/api/ontology/${routeId}`);
      expect(req2.request.method).toBe('GET');
      req2.flush([]);
    });
  });

  describe('getCacheStatistics', () => {
    it('should retrieve overall cache statistics', () => {
      const mockStats: CacheStatistics = {
        hitCount: 1000,
        missCount: 100,
        totalLoadTime: 5000,
        evictionCount: 10,
        size: 5000,
        hitRate: 0.91
      };

      service.getCacheStatistics().subscribe(stats => {
        expect(stats.hitCount).toBe(1000);
        expect(stats.size).toBe(5000);
        expect(stats.hitRate).toBe(0.91);
      });

      const req = httpMock.expectOne('/queryrest/api/ontology/cache/statistics');
      expect(req.request.method).toBe('GET');
      req.flush(mockStats);
    });
  });

  describe('warmCache', () => {
    it('should warm up cache for a route', () => {
      const routeId = 'test-route';

      service.warmCache(routeId).subscribe(response => {
        expect(response).toBeTruthy();
      });

      const req = httpMock.expectOne(`/queryrest/api/ontology/${routeId}/warm`);
      expect(req.request.method).toBe('POST');
      req.flush({ success: true });
    });

    it('should return null on warm cache error', () => {
      const routeId = 'test-route';

      service.warmCache(routeId).subscribe(response => {
        expect(response).toBeNull();
      });

      const req = httpMock.expectOne(`/queryrest/api/ontology/${routeId}/warm`);
      req.error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });
    });
  });

  describe('formatElementForDisplay', () => {
    it('should format class elements', () => {
      const element: OntologyElement = {
        uri: 'http://example.org/Person',
        label: 'Person',
        type: 'class'
      };

      const formatted = service.formatElementForDisplay(element);
      expect(formatted).toBe('Person (Class)');
    });

    it('should format object property elements', () => {
      const element: OntologyElement = {
        uri: 'http://example.org/hasName',
        label: 'hasName',
        type: 'objectProperty'
      };

      const formatted = service.formatElementForDisplay(element);
      expect(formatted).toBe('hasName (Object Property)');
    });

    it('should format datatype property elements', () => {
      const element: OntologyElement = {
        uri: 'http://example.org/age',
        label: 'age',
        type: 'datatypeProperty'
      };

      const formatted = service.formatElementForDisplay(element);
      expect(formatted).toBe('age (Datatype Property)');
    });

    it('should format annotation property elements', () => {
      const element: OntologyElement = {
        uri: 'http://example.org/comment',
        label: 'comment',
        type: 'annotationProperty'
      };

      const formatted = service.formatElementForDisplay(element);
      expect(formatted).toBe('comment (Annotation Property)');
    });

    it('should format individual elements', () => {
      const element: OntologyElement = {
        uri: 'http://example.org/JohnDoe',
        label: 'John Doe',
        type: 'individual'
      };

      const formatted = service.formatElementForDisplay(element);
      expect(formatted).toBe('John Doe (Individual)');
    });
  });
});
