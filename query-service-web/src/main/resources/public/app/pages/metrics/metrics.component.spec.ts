import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';

import { MetricsComponent } from './metrics.component';

describe('MetricsComponent', () => {
  let component: MetricsComponent;
  let fixture: ComponentFixture<MetricsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [MetricsComponent],
      imports: [
        HttpClientTestingModule,
        MatAutocompleteModule,
        BrowserAnimationsModule
      ],
      schemas: [NO_ERRORS_SCHEMA]
    })
    .compileComponents();

    fixture = TestBed.createComponent(MetricsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  const metric = (route: string, total: number, completed: number, processing: number): any => ({
    route, exchangesTotal: total, exchangesCompleted: completed, exchangesFailed: total - completed,
    exchangesInflight: 0, totalProcessingTime: processing, meanProcessingTime: 0, minProcessingTime: 0,
    maxProcessingTime: 0, uptime: '1m', state: 'Started', timeStamp: null
  });

  // Regression: with no traffic yet the summary showed "NaNms" and "NaN%", and the gauge drew NaN paths.
  it('summarises routes with no exchanges without NaN', () => {
    component.generateCustomMetrics({ metrics: [metric('a', 0, 0, 0), metric('b', 0, 0, 0)] } as any);
    expect(component.cardMetrics[1].value).toBe('0.00ms');
    expect(component.gaugeMetric).toBe(0);
    expect(JSON.stringify(component.cardMetrics)).not.toContain('NaN');
  });

  it('summarises routes with no routes at all without NaN', () => {
    component.generateCustomMetrics({ metrics: [] } as any);
    expect(component.cardMetrics[0].value).toBe(0);
    expect(component.cardMetrics[1].value).toBe('0.00ms');
    expect(component.gaugeMetric).toBe(0);
  });

  it('computes average processing time and success rate', () => {
    component.generateCustomMetrics({ metrics: [metric('a', 3, 3, 30), metric('b', 1, 0, 10)] } as any);
    expect(component.cardMetrics[1].value).toBe('10.00ms');
    expect(component.cardMetrics[2].value).toBe('a');
    expect(component.gaugeMetric).toBe(75);
  });
});
