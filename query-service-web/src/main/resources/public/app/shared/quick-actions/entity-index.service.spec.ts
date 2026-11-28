import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { EntityIndexService, REUSE_MS } from './entity-index.service';

describe('EntityIndexService', () => {
  let service: EntityIndexService;
  let http: HttpTestingController;

  const answer = () => {
    http.expectOne('/queryrest/api/routes/').flush([
      { routeId: 'zeta', description: null, status: 'Stopped', datasources: { dataSourceId: 'ds1' } },
      { routeId: 'alpha', description: 'First', status: 'Started', datasources: { dataSourceId: 'ds1' } }
    ]);
    http.expectOne('/queryrest/api/datasources/').flush([{ dataSourceId: 'ds1', url: 'http://anzo', status: 'UP' }]);
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(EntityIndexService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('fetches nothing until asked', () => {
    http.expectNone('/queryrest/api/routes/');
    expect(service.routes()).toEqual([]);
  });

  it('loads routes and datasources, sorted by id', () => {
    service.refresh(1000);
    expect(service.loading()).toBe(true);
    answer();
    expect(service.loading()).toBe(false);
    expect(service.routes()).toEqual([
      { routeId: 'alpha', description: 'First', status: 'Started', dataSourceId: 'ds1' },
      { routeId: 'zeta', description: '', status: 'Stopped', dataSourceId: 'ds1' }
    ]);
    expect(service.datasources()).toEqual([{ dataSourceId: 'ds1', url: 'http://anzo', status: 'UP' }]);
  });

  it('reuses the lists for 30 seconds, then fetches again', () => {
    service.refresh(1000);
    answer();
    service.refresh(1000 + REUSE_MS - 1);
    http.expectNone('/queryrest/api/routes/');
    service.refresh(1000 + REUSE_MS);
    answer();
  });

  it('fetches again on the next refresh after invalidate()', () => {
    service.refresh(1000);
    answer();
    service.invalidate();
    service.refresh(2000);
    answer();
  });

  it('does not start a second fetch while one is under way', () => {
    service.refresh(1000);
    service.refresh(1001);
    answer();
  });

  it('keeps the other list when one fails', () => {
    service.refresh(1000);
    http.expectOne('/queryrest/api/routes/').flush('no', { status: 500, statusText: 'Error' });
    http.expectOne('/queryrest/api/datasources/').flush([{ dataSourceId: 'ds1', url: 'u' }]);
    expect(service.routes()).toEqual([]);
    expect(service.datasources().length).toBe(1);
  });
});
