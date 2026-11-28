import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ActivatedRoute, convertToParamMap, ParamMap } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { SettingsComponent } from './settings.component';
import { SsoInfo } from '../../core/services/settings.service';

describe('SettingsComponent', () => {
  let component: SettingsComponent;
  let fixture: ComponentFixture<SettingsComponent>;
  let queryParams: BehaviorSubject<ParamMap>;

  const sso = (overrides: Partial<SsoInfo>): SsoInfo => ({
    enabled: true, issuer: 'https://sso.example.com/realms/x', clientId: 'qtt', clientSecretConfigured: true,
    applicationType: 'hybrid', usernameClaim: 'preferred_username', rolesClaim: null, rolesSource: null, audience: [],
    groupMappings: { viewer: [], editor: [], admin: [] }, sessionKey: 'client secret', privateCaTrustStore: false,
    forceHttpsRedirect: false, logoutPath: '/logout', ...overrides
  });

  beforeEach(async () => {
    queryParams = new BehaviorSubject(convertToParamMap({}));
    await TestBed.configureTestingModule({
      declarations: [SettingsComponent],
      imports: [HttpClientTestingModule],
      providers: [{ provide: ActivatedRoute, useValue: { queryParamMap: queryParams } }],
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

  it('shows the section named in the URL, and the overview for anything else', () => {
    expect(component.section).toBe('overview');
    queryParams.next(convertToParamMap({ section: 'caching' }));
    expect(component.section).toBe('caching');
    queryParams.next(convertToParamMap({ section: 'nope' }));
    expect(component.section).toBe('overview');
    expect(component.sectionParams('overview')).toEqual({ section: null });
    expect(component.sectionParams('access')).toEqual({ section: 'access' });
  });

  it('flags sign-in that is off and routes anyone can call', () => {
    expect(component.signInStatus.tone).toBe('pending');
    component.ssoInfo = sso({ enabled: false });
    expect(component.signInStatus.tone).toBe('warn');
    expect(component.needsAttention('sign-in')).toBeTrue();

    component.ssoInfo = sso({ queryRoutes: 48, openQueryRoutes: 3 });
    expect(component.signInStatus).toEqual(jasmine.objectContaining({ tone: 'ok', text: 'On · sso.example.com' }));
    expect(component.queryAccessStatus).toEqual(jasmine.objectContaining({ tone: 'warn', text: '3 of 48 routes are open to anyone' }));
    expect(component.needsAttention('access')).toBeTrue();
    expect(component.needsAttention('sign-in')).toBeFalse();

    component.ssoInfo = sso({ queryRoutes: 48, openQueryRoutes: 0 });
    expect(component.queryAccessStatus.tone).toBe('ok');
  });

  it('describes SSO group mappings and the session key', () => {
    expect(component.formatGroups(['a', 'b'])).toBe('a, b');
    expect(component.formatGroups([])).toContain('assigned directly');
    expect(component.formatSessionKey('OIDC_SESSION_SECRET')).toContain('Dedicated');
    expect(component.formatSessionKey('generated at startup')).toContain('sessions end on restart');
    expect(component.formatRolesSource('idtoken')).toBe('from the ID token');
    expect(component.formatRolesSource(null)).toBe('from the access token');
  });

  it('formats cache TTLs and numbers', () => {
    expect(component.formatTtl(3600)).toBe('1 hour');
    expect(component.formatTtl(7200)).toBe('2 hours');
    expect(component.formatTtl(90)).toBe('90 seconds');
    expect(component.formatNumber(2785.3, 1)).toBe((2785.3).toLocaleString(undefined, { maximumFractionDigits: 1 }));
  });
});
