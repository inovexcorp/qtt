import { Component, ViewChild, OnInit, OnDestroy, ChangeDetectionStrategy } from '@angular/core';
import { MatDrawer } from '@angular/material/sidenav';
import { DrawerService } from './core/services/drawer.service';
import { Subscription } from 'rxjs';
import { AuthService } from './core/services/auth.service';

@Component({
    selector: 'app-root',
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.scss'],
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
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
