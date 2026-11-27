import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';

import { SettingsComponent } from './settings.component';

describe('SettingsComponent', () => {
  let component: SettingsComponent;
  let fixture: ComponentFixture<SettingsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [SettingsComponent],
      imports: [HttpClientTestingModule],
      schemas: [NO_ERRORS_SCHEMA]
    })
    .compileComponents();

    fixture = TestBed.createComponent(SettingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('describes SSO group mappings and the session key', () => {
    expect(component.formatGroups(['a', 'b'])).toBe('a, b');
    expect(component.formatGroups([])).toContain('assigned directly');
    expect(component.formatSessionKey('OIDC_SESSION_SECRET')).toContain('Dedicated');
    expect(component.formatSessionKey('generated at startup')).toContain('sessions end on restart');
    expect(component.formatRolesSource('idtoken')).toBe('from the ID token');
    expect(component.formatRolesSource(null)).toBe('from the access token');
  });
});
