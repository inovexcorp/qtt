import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DeleteRouteModalComponent } from './delete-route-modal.component';

describe('DeleteModalComponent', () => {
  let component: DeleteRouteModalComponent;
  let fixture: ComponentFixture<DeleteRouteModalComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
    declarations: [DeleteRouteModalComponent]
})
    .compileComponents();

    fixture = TestBed.createComponent(DeleteRouteModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
