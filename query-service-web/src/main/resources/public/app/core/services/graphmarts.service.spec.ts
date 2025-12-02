import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { GraphmartsService } from './graphmarts.service';
import { Graphmarts } from '../models/graphmarts';

describe('GraphmartsService', () => {
  let service: GraphmartsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(GraphmartsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getGraphMarts', () => {
    it('should retrieve graphmarts for a datasource', () => {
      const dataSourceId = 'datasource-123';
      const mockGraphmarts: Graphmarts[] = [
        { iri: 'http://example.org/graphmart1', active: 'true', title: 'Graphmart 1' } as Graphmarts,
        { iri: 'http://example.org/graphmart2', active: 'true', title: 'Graphmart 2' } as Graphmarts
      ];

      service.getGraphMarts(dataSourceId).subscribe(graphmarts => {
        expect(graphmarts.length).toBe(2);
        expect(graphmarts[0].title).toBe('Graphmart 1');
        expect(graphmarts[1].title).toBe('Graphmart 2');
      });

      const req = httpMock.expectOne('/queryrest/api/anzo/graphmarts/datasource-123');
      expect(req.request.method).toBe('GET');
      req.flush(mockGraphmarts);
    });

    it('should handle empty graphmarts list', () => {
      const dataSourceId = 'datasource-456';

      service.getGraphMarts(dataSourceId).subscribe(graphmarts => {
        expect(graphmarts.length).toBe(0);
      });

      const req = httpMock.expectOne('/queryrest/api/anzo/graphmarts/datasource-456');
      req.flush([]);
    });

    it('should handle errors when getting graphmarts', () => {
      const dataSourceId = 'datasource-789';

      service.getGraphMarts(dataSourceId).subscribe(
        () => fail('should have failed'),
        error => {
          expect(error).toBeTruthy();
        }
      );

      const req = httpMock.expectOne('/queryrest/api/anzo/graphmarts/datasource-789');
      req.error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });
    });
  });
});
