import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { RouteAccessComponent } from './route-access.component';

describe('RouteAccessComponent', () => {
  let fixture: ComponentFixture<RouteAccessComponent>;
  let component: RouteAccessComponent;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, RouteAccessComponent],
      schemas: [NO_ERRORS_SCHEMA]
    }).compileComponents();
    fixture = TestBed.createComponent(RouteAccessComponent);
    component = fixture.componentInstance;
    component.group = new FormGroup({
      accessPolicy: new FormControl('default'),
      requiredRoles: new FormControl('')
    });
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  it('follows the server default until the route sets its own access', () => {
    http.expectOne('/queryrest/api/settings/sso').flush({ queryAuthDefault: 'authenticated' });
    expect(component.serverDefault).toBe('authenticated');
    expect(component.effective).toBe('authenticated');
    component.group.get('accessPolicy')!.setValue('open');
    expect(component.effective).toBe('open');
  });

  it('assumes open when the default cannot be loaded', () => {
    http.expectOne('/queryrest/api/settings/sso').flush('no', { status: 403, statusText: 'Forbidden' });
    expect(component.effective).toBe('open');
  });
});
