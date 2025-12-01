import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';

import { DeleteDatasourceModalComponent } from './delete-datasource-modal.component';

describe('ConfirmDialogComponent', () => {
  let component: DeleteDatasourceModalComponent;
  let fixture: ComponentFixture<DeleteDatasourceModalComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [DeleteDatasourceModalComponent],
      imports: [HttpClientTestingModule],
      providers: [
        {
          provide: MAT_DIALOG_DATA,
          useValue: {
            dataSourceId: '1',
            associatedRoutes: []
          }
        },
        { provide: MatDialogRef, useValue: { close: () => {} } }
      ],
      schemas: [NO_ERRORS_SCHEMA]
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
