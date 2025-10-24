import { Component, OnInit, ViewChild } from '@angular/core';
import { Routes } from '../../core/models/routes-interfaces/routes';
import { TableData } from '../../core/models/routes-interfaces/table-data';
import { RoutesService } from './routes.service';
import { Router, NavigationEnd } from '@angular/router';
import { MatTableDataSource } from '@angular/material/table';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { MatDialog } from '@angular/material/dialog';
import { AddRouteComponent } from '../add-route/add-route.component';
import { DeleteRouteModalComponent } from '../delete-route-modal/delete-route-modal.component';
import { filter } from 'rxjs';

@Component({
  selector: 'app-routes',
  templateUrl: './routes.component.html',
  styleUrls: ['./routes.component.scss']
})
export class RoutesComponent implements OnInit {

  routes: Routes[] = []
  dataSource = new MatTableDataSource<TableData>;
  displayedColumns: string[] = ['Status', 'Route ID', 'URL', 'Description', 'DataSource ID', 'Menu Actions'];
  baseUrl!: string;

  // Store sort state in memory
  private sortState: { active: string, direction: 'asc' | 'desc' | '' } = {
    active: 'routeId',
    direction: 'asc'
  };

  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;

  constructor(private routeService: RoutesService, public router: Router, private dialogRef: MatDialog) { }



  ngOnInit(): void {
    // Sets up listener for router changes. If you navigate to /routes, re-get all the routes
    this.router.events.pipe(filter(event => event instanceof NavigationEnd)).subscribe(() => {
      if (this.router.url === '/routes' || this.router.url.startsWith('/#/routes') && !this.router.url.includes('config-route')) {
        this.getRoutes();
      }
    });
    this.routeService.getBaseUrl().subscribe(response => {
      this.baseUrl = response.baseUrl;
      this.getRoutes();
    });
  }

  ngAfterViewInit(): void {
    // Listen for sort changes to capture user interactions
    if (this.sort) {
      this.sort.sortChange.subscribe(() => {
        this.sortState = {
          active: this.sort.active,
          direction: this.sort.direction
        };
      });
    }
  }

  // Display all routes. Needed to map to new interface b/c search-filter doesn't account for sub-arrays (datasourceId's)
  getRoutes(): void {
    this.routeService.getRoutes()
      .subscribe((routes) => {
        this.routes = routes;
        let tableData = this.routes.map(value => {
          //Get file name without path
          // let stringArray = value.templateLocation.split('/');
          let templateName = value.routeId;
          return <TableData>{
            routeId: value.routeId,
            description: value.description,
            datasource: value.datasources.dataSourceId,
            templateName: templateName,
            routeParams: value.routeParams,
            datasources: value.datasources,
            status: value.status,
            graphMartUri: value.graphMartUri
          }
        });
        this.dataSource = new MatTableDataSource(tableData);
        this.dataSource.paginator = this.paginator;
        this.dataSource.sort = this.sort;

        // Apply default sort by routeId to maintain consistent order
        if (this.sort) {
          this.sort.active = 'routeId';
          this.sort.direction = 'asc';
          this.dataSource.sort = this.sort;
        }
      });

  }

  //On key press, update the table based on search
  applyFilter(filterValue: string) {
    this.dataSource.filter = filterValue.trim();
  }

  openAddRouteDialog() {
    this.dialogRef.open(AddRouteComponent, {
      maxWidth: '150rem'
    })
  }

  openDeleteDialog(routeId: string) {
    this.dialogRef.open(DeleteRouteModalComponent, {
      data: {
        routeId: routeId
      }
    });
  }

  navigateToConfigRoute(routeId: string) {
    this.router.navigate(['routes/config-route', routeId]);
  }

  toggleRouteStatus(routeId: string, status: string) {
    this.routeService.updateRouteStatus(routeId, status).subscribe(() => {
      this.getRoutes();
    });
  }

  cloneRoute(routeId: string) {
    this.routeService.cloneRoute(routeId).subscribe(() => {
      this.getRoutes();
    });
  }

  // Check if datasource is healthy
  isDatasourceHealthy(element: TableData): boolean {
    return element.datasources?.status === 'UP' || element.datasources?.status === 'CHECKING';
  }

  // Check if datasource is disabled
  isDatasourceDisabled(element: TableData): boolean {
    return element.datasources?.status === 'DISABLED';
  }

  // Check if datasource is down
  isDatasourceDown(element: TableData): boolean {
    return element.datasources?.status === 'DOWN';
  }

  // Get tooltip message for disabled actions
  getDisabledTooltip(element: TableData): string {
    if (this.isDatasourceDisabled(element)) {
      return `Cannot start route - datasource '${element.datasources.dataSourceId}' is disabled`;
    }
    if (this.isDatasourceDown(element)) {
      return `Cannot start route - datasource '${element.datasources.dataSourceId}' is down`;
    }
    return '';
  }
}
