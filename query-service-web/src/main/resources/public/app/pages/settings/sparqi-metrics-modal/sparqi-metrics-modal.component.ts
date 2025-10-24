import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { SparqiService } from '../../../core/services/sparqi.service';
import { SparqiMetricRecord } from '../../../core/models/sparqi.models';
import { ScaleType } from '@swimlane/ngx-charts';

@Component({
  selector: 'app-sparqi-metrics-modal',
  templateUrl: './sparqi-metrics-modal.component.html',
  styleUrls: ['./sparqi-metrics-modal.component.scss']
})
export class SparqiMetricsModalComponent implements OnInit {

  // Chart data
  sparqiChartData: any[] | undefined;
  sparqiPieChartData: any[] | undefined;
  isLoading = false;

  // Color schemes
  sparqiChartColorScheme = {
    name: 'sparqi',
    selectable: true,
    group: ScaleType.Ordinal,
    domain: ['#5AA454', '#A10A28', '#C7B42C', '#AAAAAA']
  };
  sparqiPieColorScheme = {
    name: 'sparqiPie',
    selectable: true,
    group: ScaleType.Ordinal,
    domain: ['#5AA454', '#E44D25', '#CFC0BB', '#7aa3e5', '#a8385d', '#aae3f5']
  };

  constructor(
    public dialogRef: MatDialogRef<SparqiMetricsModalComponent>,
    @Inject(MAT_DIALOG_DATA) public data: any,
    private sparqiService: SparqiService
  ) { }

  ngOnInit(): void {
    this.loadChartsData();
  }

  loadChartsData(): void {
    this.isLoading = true;

    // Load history for line chart (last 7 days = 168 hours)
    this.sparqiService.getMetricsHistory(168).subscribe({
      next: (history) => {
        this.sparqiChartData = this.formatChartData(history);
        this.isLoading = false;
      },
      error: (error) => {
        console.error('Failed to load SPARQi metrics history:', error);
        this.isLoading = false;
      }
    });

    // Load tokens by route for pie chart
    this.sparqiService.getTokensByRoute().subscribe({
      next: (tokensByRoute) => {
        this.sparqiPieChartData = this.formatPieChartData(tokensByRoute);
      },
      error: (error) => {
        console.error('Failed to load SPARQi tokens by route:', error);
      }
    });
  }

  formatChartData(history: SparqiMetricRecord[]): any[] {
    if (!history || history.length === 0) {
      return [];
    }

    return [
      {
        name: 'Token Usage',
        series: history.map(record => ({
          name: new Date(record.timestamp),
          value: record.totalTokens
        }))
      }
    ];
  }

  formatPieChartData(tokensByRoute: Map<string, number>): any[] {
    if (!tokensByRoute || tokensByRoute.size === 0) {
      return [];
    }

    const data: any[] = [];
    tokensByRoute.forEach((tokens: number, routeId: string) => {
      data.push({
        name: routeId || 'Unknown',
        value: tokens
      });
    });

    return data;
  }

  // Format x-axis time labels
  xAxisTickFormatting = (value: any): string => {
    if (value instanceof Date) {
      const date = value;
      const month = date.toLocaleString('default', { month: 'short' });
      const day = date.getDate();
      const hours = date.getHours();
      const minutes = date.getMinutes().toString().padStart(2, '0');
      const ampm = hours >= 12 ? 'PM' : 'AM';
      const displayHours = hours % 12 || 12;

      return `${month} ${day}, ${displayHours}:${minutes} ${ampm}`;
    }
    return value;
  };

  close(): void {
    this.dialogRef.close();
  }
}
