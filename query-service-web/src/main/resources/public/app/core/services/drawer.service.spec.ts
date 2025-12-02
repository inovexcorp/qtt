import { TestBed } from '@angular/core/testing';
import { DrawerService } from './drawer.service';

describe('DrawerService', () => {
  let service: DrawerService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(DrawerService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should have closeDrawer$ observable', () => {
    expect(service.closeDrawer$).toBeDefined();
  });

  it('should emit when requestCloseDrawer is called', (done) => {
    let emitted = false;

    service.closeDrawer$.subscribe(() => {
      emitted = true;
      expect(emitted).toBe(true);
      done();
    });

    service.requestCloseDrawer();
  });

  it('should emit multiple times when requestCloseDrawer is called multiple times', () => {
    let count = 0;

    service.closeDrawer$.subscribe(() => {
      count++;
    });

    service.requestCloseDrawer();
    service.requestCloseDrawer();
    service.requestCloseDrawer();

    expect(count).toBe(3);
  });
});
