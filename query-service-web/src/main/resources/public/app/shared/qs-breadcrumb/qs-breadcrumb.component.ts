import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { BreadcrumbService } from 'xng-breadcrumb';
import { filter } from 'rxjs';

@Component({
  selector: 'app-qs-breadcrumb',
  templateUrl: './qs-breadcrumb.component.html',
  styleUrls: ['./qs-breadcrumb.component.scss']
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
