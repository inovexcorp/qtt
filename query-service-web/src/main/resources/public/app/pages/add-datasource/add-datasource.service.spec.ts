import { TestBed } from '@angular/core/testing';

import { AddDatasourceService } from './add-datasource.service';

describe('AddDatasourceService', () => {
  let service: AddDatasourceService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(AddDatasourceService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
