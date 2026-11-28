import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { ConfigDatasourceModalComponent, SaveDatasourceDialogData } from './config-datasource-modal.component';
import { DatasourceService } from '../../core/services/datasource.service';
import { Datasources } from '../../core/models/datasources';

describe('ConfigDatasourceModalComponent', () => {
  let fixture: ComponentFixture<ConfigDatasourceModalComponent>;
  let dialogRef: { close: ReturnType<typeof vi.fn>; disableClose: boolean };
  let api: { modifyDatasource: ReturnType<typeof vi.fn> };

  const data: SaveDatasourceDialogData = {
    datasource: { dataSourceId: 'ds1', url: 'https://new' } as Datasources,
    changes: [{ label: 'URL', from: 'https://old', to: 'https://new' }, { label: 'Password', from: null, to: null }],
    associatedRoutes: ['orders', 'customers'],
  };

  beforeEach(async () => {
    dialogRef = { close: vi.fn(), disableClose: false };
    api = { modifyDatasource: vi.fn().mockReturnValue(of({})) };
    await TestBed.configureTestingModule({
      imports: [ConfigDatasourceModalComponent],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: DatasourceService, useValue: api },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ConfigDatasourceModalComponent);
    fixture.detectChanges();
  });

  const text = () => fixture.nativeElement.textContent as string;

  it('lists what changes (the password without its value) and the routes affected', () => {
    expect(text()).toContain('https://old');
    expect(text()).toContain('https://new');
    expect(text()).toContain('Changed');
    expect(text()).toContain('These 2 routes use');
    expect(text()).toContain('orders');
  });

  it('saves and closes with true', () => {
    (fixture.componentInstance as any).save();
    expect(api.modifyDatasource).toHaveBeenCalledWith(data.datasource);
    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });

  it('stays open and says why when saving fails', () => {
    api.modifyDatasource.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400, error: 'A password is required' })));
    (fixture.componentInstance as any).save();
    fixture.detectChanges();
    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(text()).toContain('A password is required');
  });
});
