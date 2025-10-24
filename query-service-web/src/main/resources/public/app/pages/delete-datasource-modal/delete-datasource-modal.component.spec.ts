import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DeleteDatasourceModalComponent } from './delete-datasource-modal.component';

describe('ConfirmDialogComponent', () => {
  let component: DeleteDatasourceModalComponent;
  let fixture: ComponentFixture<DeleteDatasourceModalComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
    declarations: [DeleteDatasourceModalComponent]
})
    .compileComponents();

    fixture = TestBed.createComponent(DeleteDatasourceModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
