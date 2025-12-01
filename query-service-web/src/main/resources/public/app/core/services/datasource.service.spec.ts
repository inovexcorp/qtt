import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';

import { DatasourceService } from './datasource.service';

describe('DatasourceService', () => {
  let service: DatasourceService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(DatasourceService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
