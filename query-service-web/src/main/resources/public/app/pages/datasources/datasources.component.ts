import { Component, OnInit } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { DatasourceService } from '../../core/services/datasource.service';
import { Datasources } from '../../core/models/datasources';

import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';
import { filter } from 'rxjs';


@Component({
  selector: 'app-datasources',
  templateUrl: './datasources.component.html',
  styleUrls: ['./datasources.component.scss']
})
export class DatasourcesComponent implements OnInit {

  datasources: Datasources[] = [];
  healthCheckEnabled: boolean = true;

  constructor(
    private matIconRegistry: MatIconRegistry,
    private domSanitzer: DomSanitizer,
    public router: Router,
    private datasourceService: DatasourceService
  ) {
    this.matIconRegistry.addSvgIcon(
      'anzo-logo',
      this.domSanitzer.bypassSecurityTrustResourceUrl('assets/icons/anzo.svg')
    );
  }

  ngOnInit(): void {
    // Sets up listener for router changes. If you navigate to /datasources, re-get all the datasources
    this.router.events.pipe(filter(event => event instanceof NavigationEnd)).subscribe(() => {
      if (this.router.url === '/datasources') {
          this.getDatasources();
      }
    });
    this.getDatasources();
    this.checkHealthCheckStatus();
  }

  private checkHealthCheckStatus(): void {
    this.datasourceService.getHealthCheckConfig().subscribe({
      next: (config) => {
        this.healthCheckEnabled = config.enabled && config.available;
      },
      error: (err) => {
        console.error('Failed to fetch health check configuration:', err);
        this.healthCheckEnabled = true;
      }
    });
  }

  getDatasources(): void {
    this.datasourceService.getDatasources()
      .subscribe(datasources => this.datasources = datasources);
  }

  getStatusIcon(status?: string): string {
    switch(status) {
      case 'UP': return 'check_circle';
      case 'DOWN': return 'error';
      case 'DISABLED': return 'block';
      case 'CHECKING': return 'sync';
      default: return 'help';  // UNKNOWN
    }
  }

  getStatusClass(status?: string): string {
    switch(status) {
      case 'UP': return 'status-up';
      case 'DOWN': return 'status-down';
      case 'DISABLED': return 'status-disabled';
      case 'CHECKING': return 'status-checking';
      default: return 'status-unknown';
    }
  }

  getStatusTooltip(datasource: Datasources): string {
    if (!datasource.status) return 'Health status unknown';

    let tooltip = `Status: ${datasource.status}`;
    if (datasource.lastHealthCheck) {
      tooltip += `\nLast check: ${new Date(datasource.lastHealthCheck).toLocaleString()}`;
    }
    if (datasource.status === 'DOWN' && datasource.lastHealthError) {
      tooltip += `\nError: ${datasource.lastHealthError}`;
    }
    if (datasource.consecutiveFailures && datasource.consecutiveFailures > 0) {
      tooltip += `\nConsecutive failures: ${datasource.consecutiveFailures}`;
    }
    if (!this.healthCheckEnabled) {
      tooltip += `\n\nNote: Automated health checks are disabled`;
    }
    return tooltip;
  }

}
