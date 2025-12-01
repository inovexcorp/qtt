import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';

import { GraphmartsService } from './graphmarts.service';

describe('GraphmartsService', () => {
  let service: GraphmartsService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(GraphmartsService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
