import { TestBed } from '@angular/core/testing';

import { DeleteRouteModalService } from './delete-route-modal.service';

describe('DeleteModalService', () => {
  let service: DeleteRouteModalService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(DeleteRouteModalService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
