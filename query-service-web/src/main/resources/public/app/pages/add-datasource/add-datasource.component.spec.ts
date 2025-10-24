import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AddDatasourceComponent } from './add-datasource.component';

describe('AddDatasourceComponent', () => {
  let component: AddDatasourceComponent;
  let fixture: ComponentFixture<AddDatasourceComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
    declarations: [AddDatasourceComponent]
})
    .compileComponents();

    fixture = TestBed.createComponent(AddDatasourceComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
