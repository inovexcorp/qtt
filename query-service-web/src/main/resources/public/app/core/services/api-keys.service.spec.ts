import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ApiKeysService } from './api-keys.service';

describe('ApiKeysService', () => {
  let service: ApiKeysService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(ApiKeysService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists, creates and revokes keys', () => {
    service.list().subscribe(keys => expect(keys.length).toBe(1));
    http.expectOne({ method: 'GET', url: '/queryrest/api/apikeys' }).flush([{ id: 'a' }]);

    service.create({ name: 'app', routes: ['people'] }).subscribe(k => expect(k.key).toBe('qtt_x_y'));
    const create = http.expectOne({ method: 'POST', url: '/queryrest/api/apikeys' });
    expect(create.request.body).toEqual({ name: 'app', routes: ['people'] });
    create.flush({ id: 'x', key: 'qtt_x_y' });

    service.revoke('x').subscribe();
    http.expectOne({ method: 'DELETE', url: '/queryrest/api/apikeys/x' }).flush(null);
  });
});
