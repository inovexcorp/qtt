import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { ApiKeysComponent } from './api-keys.component';

describe('ApiKeysComponent', () => {
  let fixture: ComponentFixture<ApiKeysComponent>;
  let component: ApiKeysComponent;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, MatSnackBarModule, ApiKeysComponent],
      schemas: [NO_ERRORS_SCHEMA]
    }).compileComponents();
    fixture = TestBed.createComponent(ApiKeysComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('lists keys and hides revoked ones unless asked', () => {
    http.expectOne('/queryrest/api/apikeys').flush([
      { id: 'a', name: 'A', routes: ['p'], status: 'active' },
      { id: 'b', name: 'B', routes: ['*'], status: 'revoked' }
    ]);
    expect(component.visibleKeys.map(k => k.id)).toEqual(['a']);
    component.showRevoked = true;
    expect(component.visibleKeys.map(k => k.id)).toEqual(['a', 'b']);
  });

  it('creates a key, shows it once and reloads the list', () => {
    http.expectOne('/queryrest/api/apikeys').flush([]);
    component.form.setValue({ name: ' reporting ', description: '', routes: 'people, places', expiresInDays: null });
    component.create();
    const post = http.expectOne({ method: 'POST', url: '/queryrest/api/apikeys' });
    expect(post.request.body).toEqual({ name: 'reporting', description: undefined, routes: ['people', 'places'],
      expiresInDays: undefined });
    post.flush({ id: 'k', name: 'reporting', key: 'qtt_k_secret', routes: ['people', 'places'], status: 'active' });
    expect(component.created?.key).toBe('qtt_k_secret');
    http.expectOne('/queryrest/api/apikeys').flush([]);
    component.dismissCreated();
    expect(component.created).toBeNull();
  });

  it('keeps the create form folded away until asked for, and closes it after creating', () => {
    http.expectOne('/queryrest/api/apikeys').flush([]);
    expect(component.showForm).toBe(false);
    component.openForm();
    expect(component.showForm).toBe(true);
    component.form.setValue({ name: 'r', description: '', routes: 'p', expiresInDays: null });
    component.create();
    http.expectOne({ method: 'POST', url: '/queryrest/api/apikeys' })
      .flush({ id: 'k', name: 'r', key: 'qtt_k_s', routes: ['p'], status: 'active' });
    http.expectOne('/queryrest/api/apikeys').flush([]);
    expect(component.showForm).toBe(false);
    component.openForm();
    component.form.patchValue({ name: 'typed' });
    component.closeForm();
    expect(component.showForm).toBe(false);
    expect(component.form.value.name).toBeNull();
  });

  it('parses route lists', () => {
    http.expectOne('/queryrest/api/apikeys').flush([]);
    expect(ApiKeysComponent.parseRoutes('a, b  c,,')).toEqual(['a', 'b', 'c']);
    expect(ApiKeysComponent.parseRoutes('*')).toEqual(['*']);
    expect(ApiKeysComponent.parseRoutes(null)).toEqual([]);
  });
});
