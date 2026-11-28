import { Injectable, computed, inject, signal } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';
import { QuickAction } from './quick-action';

/**
 * The actions the quick actions bar offers. The bar registers the ones available everywhere; a page registers its
 * own when it opens and removes them when it closes:
 *
 *   private unregister = this.quickActions.register([...]);
 *   ngOnDestroy() { this.unregister(); }
 */
@Injectable({ providedIn: 'root' })
export class QuickActionsService {

  private readonly auth = inject(AuthService);
  private readonly registered = signal<readonly QuickAction[]>([]);

  /** Registered actions the current user's roles allow, in registration order. */
  readonly actions = computed(() =>
    this.registered().filter(a => this.auth.hasRole(a.minRole ?? 'viewer')));

  /** Adds actions (replacing any with the same id); returns a function that removes them again. */
  register(actions: QuickAction[]): () => void {
    const ids = new Set(actions.map(a => a.id));
    this.registered.update(list => [...list.filter(a => !ids.has(a.id)), ...actions]);
    return () => this.registered.update(list => list.filter(a => !actions.includes(a)));
  }
}
