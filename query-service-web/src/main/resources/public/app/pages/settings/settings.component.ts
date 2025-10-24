import { Component, OnInit } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { SettingsService } from '../../core/services/settings.service';
import { CacheService } from '../../core/services/cache.service';
import { SparqiService } from '../../core/services/sparqi.service';
import { CacheStatistics } from '../../core/models/ontology/cache-statistics';
import { CacheInfo, CacheStats } from '../../core/models/cache-info';
import { SparqiMetricsSummary } from '../../core/models/sparqi.models';
import { SparqiMetricsModalComponent } from './sparqi-metrics-modal/sparqi-metrics-modal.component';

@Component({
  selector: 'app-settings',
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.scss']
})
export class SettingsComponent implements OnInit {

  databaseType: string | undefined;
  databaseRemoteUrl: string | undefined;
  uptime: string | undefined;
  version: string | undefined;
  datasourceCount: number | undefined;
  routeCount: number | undefined;
  cacheStats: CacheStatistics | undefined;

  // Redis cache properties
  redisCacheInfo: CacheInfo | undefined;
  redisCacheStats: CacheStats | undefined;
  redisCacheAvailable: boolean = false;

  // SPARQi metrics properties
  sparqiMetricsSummary: SparqiMetricsSummary | undefined;
  sparqiEnabled: boolean = false;
  sparqiActiveSessions: number = 0;
  sparqiHealthChecked: boolean = false;

  constructor(
    private settingsService: SettingsService,
    private cacheService: CacheService,
    private sparqiService: SparqiService,
    private dialog: MatDialog
  ) { }

  ngOnInit(): void {
    this.settingsService.getDatabaseInfo().subscribe(info => {
      this.databaseType = info.datasourceType;
      this.databaseRemoteUrl = info.datasourceUrl;
      this.uptime = info.uptime;
      this.version = info.version;
    });

    this.settingsService.getStats().subscribe(stats => {
      this.datasourceCount = stats.datasources;
      this.routeCount = stats.routes;
    });

    this.settingsService.getCacheStatistics().subscribe(stats => {
      this.cacheStats = stats;
    });

    // Load Redis cache information and statistics
    this.cacheService.getCacheInfo().subscribe({
      next: (response: any) => {
        // Extract nested info and stats objects from the response
        this.redisCacheInfo = response.info;
        this.redisCacheStats = response.stats;
        this.redisCacheAvailable = response.available;
      },
      error: (error) => {
        console.error('Failed to load Redis cache info:', error);
        this.redisCacheAvailable = false;
      }
    });

    // Load SPARQi metrics
    this.loadSparqiMetrics();
  }

  loadSparqiMetrics(): void {
    // First check if SPARQi is enabled
    this.sparqiService.checkHealth().subscribe({
      next: (health) => {
        this.sparqiEnabled = health.status === 'available';
        this.sparqiActiveSessions = health.activeSessions;
        this.sparqiHealthChecked = true;

        // Only load metrics if SPARQi is enabled
        if (this.sparqiEnabled) {
          this.sparqiService.getMetricsSummary().subscribe({
            next: (summary) => {
              this.sparqiMetricsSummary = summary;
            },
            error: (error) => {
              console.error('Failed to load SPARQi metrics summary:', error);
            }
          });
        }
      },
      error: (error) => {
        console.error('Failed to check SPARQi health:', error);
        this.sparqiHealthChecked = true;
        this.sparqiEnabled = false;
      }
    });
  }

  openMetricsCharts(): void {
    this.dialog.open(SparqiMetricsModalComponent, {
      width: '1400px',
      maxWidth: '95vw',
      maxHeight: '90vh',
      panelClass: 'sparqi-metrics-dialog-panel',
      autoFocus: false
    });
  }

  formatHitRate(rate: number | undefined): string {
    return rate !== undefined ? (rate * 100).toFixed(2) + '%' : 'N/A';
  }

  formatLoadTime(time: number | undefined): string {
    if (time === undefined) return 'N/A';
    if (time < 1000) return time + ' ms';
    return (time / 1000).toFixed(2) + ' s';
  }

  // Redis cache helper methods
  isCacheEnabled(): boolean {
    return this.redisCacheAvailable &&
           this.redisCacheInfo !== undefined &&
           this.redisCacheInfo.enabled;
  }

  formatCacheHitRate(stats: CacheStats | undefined): string {
    if (!stats) return 'N/A';
    const total = stats.hits + stats.misses;
    if (total === 0) return '0.00%';
    const ratio = stats.hits / total;
    return (ratio * 100).toFixed(2) + '%';
  }

  formatBytes(bytes: number | undefined): string {
    if (bytes === undefined || bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i];
  }

  // SPARQi metrics helper methods
  formatCurrency(amount: number | undefined): string {
    if (amount === undefined) return '$0.00';
    return '$' + amount.toFixed(4);
  }

  formatNumber(num: number | undefined): string {
    if (num === undefined) return '0';
    return num.toLocaleString();
  }

  formatDecimal(num: number | undefined, decimals: number = 2): string {
    if (num === undefined) return '0';
    return num.toFixed(decimals);
  }
}
