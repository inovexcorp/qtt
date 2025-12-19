import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { LayersService } from './layers.service';
import { GraphmartLayers } from '../models/graphmart-layers';

describe('LayersService', () => {
  let service: LayersService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(LayersService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getGraphmartLayers', () => {
    it('should retrieve layers for a graphmart', () => {
      const dataSourceId = 'datasource-123';
      const graphmartUri = 'http://example.org/graphmart1';
      const mockLayers: GraphmartLayers[] = [
        { iri: 'http://example.org/layer1', active: 'true', title: 'Layer 1' } as GraphmartLayers,
        { iri: 'http://example.org/layer2', active: 'true', title: 'Layer 2' } as GraphmartLayers
      ];

      service.getGraphmartLayers(dataSourceId, graphmartUri).subscribe(layers => {
        expect(layers.length).toBe(2);
        expect(layers[0].title).toBe('Layer 1');
        expect(layers[1].title).toBe('Layer 2');
      });

      const req = httpMock.expectOne(request =>
        request.url.includes('/queryrest/api/anzo/layers/datasource-123') &&
        request.params.get('graphmartUri') === graphmartUri
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockLayers);
    });

    it('should handle empty layers list', () => {
      const dataSourceId = 'datasource-456';
      const graphmartUri = 'http://example.org/graphmart2';

      service.getGraphmartLayers(dataSourceId, graphmartUri).subscribe(layers => {
        expect(layers.length).toBe(0);
      });

      const req = httpMock.expectOne(request =>
        request.url.includes('/queryrest/api/anzo/layers/datasource-456')
      );
      req.flush([]);
    });

    it('should handle errors when getting graphmart layers', () => {
      const dataSourceId = 'datasource-789';
      const graphmartUri = 'http://example.org/graphmart3';

      service.getGraphmartLayers(dataSourceId, graphmartUri).subscribe(
        () => fail('should have failed'),
        error => {
          expect(error).toBeTruthy();
        }
      );

      const req = httpMock.expectOne(request =>
        request.url.includes('/queryrest/api/anzo/layers/datasource-789')
      );
      req.error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });
    });
  });

  describe('getRouteLayers', () => {
    it('should retrieve layers tied to a route', () => {
      const routeId = 'route-123';
      const mockLayers: string[] = ['layer1', 'layer2', 'layer3'];

      service.getRouteLayers(routeId).subscribe(layers => {
        expect(layers.length).toBe(3);
        expect(layers).toEqual(mockLayers);
      });

      const req = httpMock.expectOne('/queryrest/api/layers/route-123');
      expect(req.request.method).toBe('GET');
      req.flush(mockLayers);
    });

    it('should handle empty route layers', () => {
      const routeId = 'route-456';

      service.getRouteLayers(routeId).subscribe(layers => {
        expect(layers.length).toBe(0);
      });

      const req = httpMock.expectOne('/queryrest/api/layers/route-456');
      req.flush([]);
    });

    it('should handle errors when getting route layers', () => {
      const routeId = 'route-789';

      service.getRouteLayers(routeId).subscribe(
        () => fail('should have failed'),
        error => {
          expect(error).toBeTruthy();
        }
      );

      const req = httpMock.expectOne('/queryrest/api/layers/route-789');
      req.error(new ProgressEvent('error'), { status: 404, statusText: 'Not Found' });
    });
  });
});
