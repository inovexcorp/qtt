import { Component, ViewChild, OnInit, OnDestroy } from '@angular/core';
import { MatDrawer } from '@angular/material/sidenav';
import { DrawerService } from './core/services/drawer.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit, OnDestroy {
  title = 'query-service-ui';

  @ViewChild('drawer') drawer!: MatDrawer;

  private drawerSubscription?: Subscription;

  constructor(private drawerService: DrawerService) {}

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
