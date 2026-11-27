import { Directive, Input, OnDestroy, OnInit, TemplateRef, ViewContainerRef } from '@angular/core';
import { Subscription } from 'rxjs';
import { AuthService, QttRole } from '../core/services/auth.service';

/**
 * Renders its element only for users holding a role: {@code <button *qttIfRole="'editor'">}. Cosmetic: the server
 * enforces every role. With sign-in off, everyone holds every role.
 */
@Directive({ selector: '[qttIfRole]' })
export class IfRoleDirective implements OnInit, OnDestroy {

  @Input('qttIfRole') role!: QttRole;

  private shown = false;
  private subscription?: Subscription;

  constructor(private template: TemplateRef<unknown>, private container: ViewContainerRef, private auth: AuthService) {}

  ngOnInit(): void {
    this.subscription = this.auth.user$.subscribe(() => this.update());
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  private update(): void {
    const allowed = this.auth.hasRole(this.role);
    if (allowed && !this.shown) {
      this.container.createEmbeddedView(this.template);
      this.shown = true;
    } else if (!allowed && this.shown) {
      this.container.clear();
      this.shown = false;
    }
  }
}
