import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';

import { ConfigRouteService } from './config-route.service';

describe('ConfigRouteService', () => {
  let service: ConfigRouteService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(ConfigRouteService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
