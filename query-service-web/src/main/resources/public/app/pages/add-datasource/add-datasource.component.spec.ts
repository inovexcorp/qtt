import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';

import { AddDatasourceComponent } from './add-datasource.component';

describe('AddDatasourceComponent', () => {
  let component: AddDatasourceComponent;
  let fixture: ComponentFixture<AddDatasourceComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [AddDatasourceComponent],
      imports: [HttpClientTestingModule],
      schemas: [NO_ERRORS_SCHEMA]
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
