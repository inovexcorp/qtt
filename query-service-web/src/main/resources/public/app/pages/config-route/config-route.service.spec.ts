import { TestBed } from '@angular/core/testing';

import { ConfigRouteService } from './config-route.service';

describe('ConfigRouteService', () => {
  let service: ConfigRouteService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ConfigRouteService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
