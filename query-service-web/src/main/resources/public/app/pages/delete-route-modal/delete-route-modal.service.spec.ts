import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';

import { DeleteRouteModalService } from './delete-route-modal.service';

describe('DeleteModalService', () => {
  let service: DeleteRouteModalService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(DeleteRouteModalService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
