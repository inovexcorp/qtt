import { TestBed } from '@angular/core/testing';

import { GraphmartsService } from './graphmarts.service';

describe('GraphmartsService', () => {
  let service: GraphmartsService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(GraphmartsService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
