import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { BreadcrumbService, BreadcrumbComponent } from 'xng-breadcrumb';
import { filter } from 'rxjs';
import { MatToolbar } from '@angular/material/toolbar';

@Component({
    selector: 'app-qs-breadcrumb',
    templateUrl: './qs-breadcrumb.component.html',
    styleUrls: ['./qs-breadcrumb.component.scss'],
    changeDetection: ChangeDetectionStrategy.Eager,
    imports: [MatToolbar, BreadcrumbComponent]
})
export class QsBreadcrumbComponent implements OnInit {

  constructor(
    private breadcrumbService: BreadcrumbService,
    private route: ActivatedRoute,
    public router: Router
  ) { }

  ngOnInit(): void {
    // Update breadcrumb dynamically for config-datasource routes
    this.router.events.pipe(filter(event => event instanceof NavigationEnd)).subscribe(() => {
      if (this.router.url.includes("/config-datasource/")) {
        const datasourceId = this.router.url.substring(31).replace("%20", "");
        this.breadcrumbService.set('@dataSourceId', datasourceId);
      }
    });
  }
}
