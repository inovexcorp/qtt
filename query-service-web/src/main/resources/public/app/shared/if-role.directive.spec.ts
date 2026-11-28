import { Component, ChangeDetectionStrategy } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { IfRoleDirective } from './if-role.directive';
import { AuthService } from '../core/services/auth.service';

@Component({
    template: `
    <span id="viewer" *qttIfRole="'viewer'">v</span>
    <span id="editor" *qttIfRole="'editor'">e</span>
    <span id="admin" *qttIfRole="'admin'">a</span>`,
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
class HostComponent {
}

describe('IfRoleDirective', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      declarations: [HostComponent, IfRoleDirective]
    });
  });

  function shown(): string[] {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    return ['viewer', 'editor', 'admin'].filter(id => fixture.nativeElement.querySelector('#' + id));
  }

  async function signIn(roles: string[]): Promise<void> {
    const auth = TestBed.inject(AuthService);
    const loading = auth.load();
    TestBed.inject(HttpTestingController).expectOne('/queryrest/api/me').flush({
      username: 'u', displayName: 'U', roles, authEnabled: true, logoutUrl: '/logout'
    });
    await loading;
  }

  it('shows everything before the user is known (and with sign-in off)', () => {
    expect(shown()).toEqual(['viewer', 'editor', 'admin']);
  });

  it('shows only what an editor may use', async () => {
    await signIn(['qtt-viewer', 'qtt-editor']);
    expect(shown()).toEqual(['viewer', 'editor']);
  });

  it('shows only what a viewer may use', async () => {
    await signIn(['qtt-viewer']);
    expect(shown()).toEqual(['viewer']);
  });

  it('hides content when the role is lost', async () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#admin')).toBeTruthy();
    await signIn(['qtt-viewer']);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#admin')).toBeNull();
  });
});
