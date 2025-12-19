import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { SparqiMetricsModalComponent } from './sparqi-metrics-modal.component';
import { SparqiService } from '../../../core/services/sparqi.service';
import { SparqiMetricRecord } from '../../../core/models/sparqi.models';
import { NO_ERRORS_SCHEMA } from '@angular/core';

describe('SparqiMetricsModalComponent', () => {
  let component: SparqiMetricsModalComponent;
  let fixture: ComponentFixture<SparqiMetricsModalComponent>;
  let mockDialogRef: jasmine.SpyObj<MatDialogRef<SparqiMetricsModalComponent>>;
  let mockSparqiService: jasmine.SpyObj<SparqiService>;

  beforeEach(async () => {
    mockDialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    mockSparqiService = jasmine.createSpyObj('SparqiService', ['getMetricsHistory', 'getTokensByRoute']);

    await TestBed.configureTestingModule({
      declarations: [SparqiMetricsModalComponent],
      imports: [HttpClientTestingModule],
      providers: [
        { provide: MatDialogRef, useValue: mockDialogRef },
        { provide: MAT_DIALOG_DATA, useValue: {} },
        { provide: SparqiService, useValue: mockSparqiService }
      ],
      schemas: [NO_ERRORS_SCHEMA]
    }).compileComponents();

    fixture = TestBed.createComponent(SparqiMetricsModalComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load charts data on init', () => {
    const mockHistory: SparqiMetricRecord[] = [
      {
        id: 1,
        timestamp: new Date(),
        inputTokens: 50,
        outputTokens: 50,
        totalTokens: 100,
        messageCount: 5,
        sessionId: 'session-1',
        userId: 'user-1',
        routeId: 'route-1',
        modelName: 'claude-3-sonnet',
        toolCallCount: 1,
        estimatedCost: 0.005
      }
    ];
    const mockTokensByRoute = new Map([['route-1', 1000]]);

    mockSparqiService.getMetricsHistory.and.returnValue(of(mockHistory));
    mockSparqiService.getTokensByRoute.and.returnValue(of(mockTokensByRoute));

    component.ngOnInit();

    expect(mockSparqiService.getMetricsHistory).toHaveBeenCalledWith(168);
    expect(mockSparqiService.getTokensByRoute).toHaveBeenCalled();
    expect(component.isLoading).toBe(false);
    expect(component.sparqiChartData).toBeDefined();
    expect(component.sparqiPieChartData).toBeDefined();
  });

  it('should handle error when loading metrics history', () => {
    const mockTokensByRoute = new Map([['route-1', 1000]]);
    mockSparqiService.getMetricsHistory.and.returnValue(throwError(() => new Error('Failed to load')));
    mockSparqiService.getTokensByRoute.and.returnValue(of(mockTokensByRoute));

    spyOn(console, 'error');

    component.ngOnInit();

    expect(component.isLoading).toBe(false);
    expect(console.error).toHaveBeenCalled();
  });

  it('should handle error when loading tokens by route', () => {
    const mockHistory: SparqiMetricRecord[] = [];
    mockSparqiService.getMetricsHistory.and.returnValue(of(mockHistory));
    mockSparqiService.getTokensByRoute.and.returnValue(throwError(() => new Error('Failed to load')));

    spyOn(console, 'error');

    component.ngOnInit();

    expect(console.error).toHaveBeenCalled();
  });

  describe('formatChartData', () => {
    it('should format history data correctly', () => {
      const history: SparqiMetricRecord[] = [
        {
          id: 1,
          timestamp: new Date('2025-01-01'),
          inputTokens: 50,
          outputTokens: 50,
          totalTokens: 100,
          messageCount: 5,
          sessionId: 'session-1',
          userId: 'user-1',
          routeId: 'route-1',
          modelName: 'claude-3-sonnet',
          toolCallCount: 1,
          estimatedCost: 0.005
        },
        {
          id: 2,
          timestamp: new Date('2025-01-02'),
          inputTokens: 100,
          outputTokens: 100,
          totalTokens: 200,
          messageCount: 10,
          sessionId: 'session-2',
          userId: 'user-1',
          routeId: 'route-1',
          modelName: 'claude-3-sonnet',
          toolCallCount: 2,
          estimatedCost: 0.010
        }
      ];

      const formatted = component.formatChartData(history);

      expect(formatted.length).toBe(1);
      expect(formatted[0].name).toBe('Token Usage');
      expect(formatted[0].series.length).toBe(2);
      expect(formatted[0].series[0].value).toBe(100);
      expect(formatted[0].series[1].value).toBe(200);
    });

    it('should return empty array for empty history', () => {
      const formatted = component.formatChartData([]);
      expect(formatted).toEqual([]);
    });

    it('should return empty array for null history', () => {
      const formatted = component.formatChartData(null as any);
      expect(formatted).toEqual([]);
    });
  });

  describe('formatPieChartData', () => {
    it('should format tokens by route data correctly', () => {
      const tokensByRoute = new Map([
        ['route-1', 1000],
        ['route-2', 2000],
        ['route-3', 1500]
      ]);

      const formatted = component.formatPieChartData(tokensByRoute);

      expect(formatted.length).toBe(3);
      expect(formatted.find(d => d.name === 'route-1')?.value).toBe(1000);
      expect(formatted.find(d => d.name === 'route-2')?.value).toBe(2000);
      expect(formatted.find(d => d.name === 'route-3')?.value).toBe(1500);
    });

    it('should return empty array for empty map', () => {
      const formatted = component.formatPieChartData(new Map());
      expect(formatted).toEqual([]);
    });

    it('should return empty array for null map', () => {
      const formatted = component.formatPieChartData(null as any);
      expect(formatted).toEqual([]);
    });

    it('should handle unknown route IDs', () => {
      const tokensByRoute = new Map([['', 500]]);
      const formatted = component.formatPieChartData(tokensByRoute);
      expect(formatted[0].name).toBe('Unknown');
    });
  });

  describe('xAxisTickFormatting', () => {
    it('should format Date objects correctly', () => {
      const date = new Date('2025-01-15 14:30:00');
      const formatted = component.xAxisTickFormatting(date);
      expect(formatted).toContain('Jan 15');
      expect(formatted).toMatch(/\d{1,2}:\d{2} (AM|PM)/);
    });

    it('should return value as-is if not a Date', () => {
      const value = 'test-value';
      const formatted = component.xAxisTickFormatting(value);
      expect(formatted).toBe('test-value');
    });

    it('should format midnight correctly', () => {
      const date = new Date('2025-01-15 00:00:00');
      const formatted = component.xAxisTickFormatting(date);
      expect(formatted).toContain('12:00 AM');
    });

    it('should format noon correctly', () => {
      const date = new Date('2025-01-15 12:00:00');
      const formatted = component.xAxisTickFormatting(date);
      expect(formatted).toContain('12:00 PM');
    });

    it('should pad minutes with zero', () => {
      const date = new Date('2025-01-15 14:05:00');
      const formatted = component.xAxisTickFormatting(date);
      expect(formatted).toContain(':05');
    });
  });

  describe('close', () => {
    it('should close the dialog', () => {
      component.close();
      expect(mockDialogRef.close).toHaveBeenCalled();
    });
  });

  it('should have correct color schemes', () => {
    expect(component.sparqiChartColorScheme).toBeDefined();
    expect(component.sparqiChartColorScheme.domain.length).toBeGreaterThan(0);
    expect(component.sparqiPieColorScheme).toBeDefined();
    expect(component.sparqiPieColorScheme.domain.length).toBeGreaterThan(0);
  });
});
