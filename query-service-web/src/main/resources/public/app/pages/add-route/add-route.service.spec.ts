import { TestBed } from '@angular/core/testing';

import { AddRouteService } from './add-route.service';

describe('AddRouteService', () => {
  let service: AddRouteService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(AddRouteService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
