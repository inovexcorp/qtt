import { ComponentFixture, TestBed } from '@angular/core/testing';

import { QsBreadcrumbComponent } from './qs-breadcrumb.component';

describe('QsBreadcrumbComponent', () => {
  let component: QsBreadcrumbComponent;
  let fixture: ComponentFixture<QsBreadcrumbComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
    declarations: [QsBreadcrumbComponent]
})
    .compileComponents();

    fixture = TestBed.createComponent(QsBreadcrumbComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
