import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';

import { ConfigDatasourceModalComponent } from './config-datasource-modal.component';

describe('ConfigDatasourceModalComponent', () => {
  let component: ConfigDatasourceModalComponent;
  let fixture: ComponentFixture<ConfigDatasourceModalComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ConfigDatasourceModalComponent],
      imports: [HttpClientTestingModule],
      providers: [
        {
          provide: MAT_DIALOG_DATA,
          useValue: {
            datasource: { dataSourceId: '1', name: 'Test Datasource' },
            associatedRoutes: []
          }
        },
        { provide: MatDialogRef, useValue: { close: () => {} } },
        { provide: MatDialog, useValue: { closeAll: () => {} } }
      ],
      schemas: [NO_ERRORS_SCHEMA]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ConfigDatasourceModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
