import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';

import { AddRouteService } from './add-route.service';

describe('AddRouteService', () => {
  let service: AddRouteService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(AddRouteService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
