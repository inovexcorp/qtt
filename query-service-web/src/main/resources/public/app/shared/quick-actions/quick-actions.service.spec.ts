import { TestBed } from '@angular/core/testing';
import { AuthService, QttRole } from '../../core/services/auth.service';
import { QuickAction } from './quick-action';
import { QuickActionsService } from './quick-actions.service';

describe('QuickActionsService', () => {
  let roles: QttRole[];
  let service: QuickActionsService;
  const action = (id: string, minRole?: QttRole): QuickAction =>
    ({ id, label: id, icon: 'bolt', group: 'page', minRole, run: () => undefined });

  beforeEach(() => {
    roles = ['viewer'];
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: { hasRole: (r: QttRole) => roles.includes(r) } }]
    });
    service = TestBed.inject(QuickActionsService);
  });

  it('adds actions and removes them with the returned function', () => {
    const remove = service.register([action('a'), action('b')]);
    const keep = service.register([action('c')]);
    expect(service.actions().map(a => a.id)).toEqual(['a', 'b', 'c']);
    remove();
    expect(service.actions().map(a => a.id)).toEqual(['c']);
    keep();
    expect(service.actions()).toEqual([]);
  });

  it('replaces an action registered again with the same id, and the old removal leaves the new one', () => {
    const first = action('a');
    const removeFirst = service.register([first]);
    const second = { ...action('a'), label: 'second' };
    service.register([second]);
    expect(service.actions()).toEqual([second]);
    removeFirst();
    expect(service.actions()).toEqual([second]);
  });

  it('hides actions above the user\'s role', () => {
    service.register([action('view'), action('edit', 'editor'), action('admin', 'admin')]);
    expect(service.actions().map(a => a.id)).toEqual(['view']);
  });

  it('shows editor actions to editors', () => {
    roles = ['viewer', 'editor'];
    service.register([action('view'), action('edit', 'editor'), action('admin', 'admin')]);
    expect(service.actions().map(a => a.id)).toEqual(['view', 'edit']);
  });
});
