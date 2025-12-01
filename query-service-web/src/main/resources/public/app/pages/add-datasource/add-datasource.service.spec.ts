import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';

import { AddDatasourceService } from './add-datasource.service';

describe('AddDatasourceService', () => {
  let service: AddDatasourceService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(AddDatasourceService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
