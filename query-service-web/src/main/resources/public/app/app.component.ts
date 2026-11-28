import { Component, ViewChild, OnInit, OnDestroy, ChangeDetectionStrategy } from '@angular/core';
import { MatDrawer, MatDrawerContainer, MatDrawerContent } from '@angular/material/sidenav';
import { DrawerService } from './core/services/drawer.service';
import { Subscription } from 'rxjs';
import { AuthService } from './core/services/auth.service';
import { MatToolbar } from '@angular/material/toolbar';
import { MatIconButton, MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatMenuTrigger, MatMenu, MatMenuItem } from '@angular/material/menu';
import { QsBreadcrumbComponent } from './shared/qs-breadcrumb/qs-breadcrumb.component';
import { MatCard, MatCardHeader, MatCardAvatar, MatCardTitle, MatCardSubtitle, MatCardContent, MatCardActions } from '@angular/material/card';
import { MatNavList, MatListItem } from '@angular/material/list';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { QuickActionsBarComponent } from './shared/quick-actions/quick-actions-bar.component';

@Component({
    selector: 'app-root',
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.scss'],
    changeDetection: ChangeDetectionStrategy.Eager,
    imports: [MatToolbar, MatIconButton, MatIcon, MatButton, MatMenuTrigger, MatMenu, MatMenuItem, QsBreadcrumbComponent, MatCard, MatCardHeader, MatCardAvatar, MatCardTitle, MatCardSubtitle, MatCardContent, MatCardActions, MatDrawerContainer, MatDrawer, MatNavList, MatListItem, RouterLink, RouterLinkActive, MatDrawerContent, RouterOutlet, QuickActionsBarComponent]
})
export class AppComponent implements OnInit, OnDestroy {
  title = 'query-service-ui';

  /** Absent while the "no access" page is shown. */
  @ViewChild('drawer') drawer?: MatDrawer;

  private drawerSubscription?: Subscription;

  constructor(private drawerService: DrawerService, public auth: AuthService) {}

  ngOnInit(): void {
    this.drawerSubscription = this.drawerService.closeDrawer$.subscribe(() => {
      if (this.drawer) {
        this.drawer.close();
      }
    });
  }

  ngOnDestroy(): void {
    this.drawerSubscription?.unsubscribe();
  }
}
