import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';

import { LayersService } from './layers.service';

describe('LayersService', () => {
  let service: LayersService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(LayersService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
