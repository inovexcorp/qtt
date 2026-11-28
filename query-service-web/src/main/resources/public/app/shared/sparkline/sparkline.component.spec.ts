import { TestBed } from '@angular/core/testing';
import { isShift, SparklineComponent, sparkPaths, sparkPoints, sparkSummary } from './sparkline.component';

describe('sparkline', () => {
  it('spreads points across the width and scales to the largest value', () => {
    const points = sparkPoints([0, 5, 10]);
    expect(points.map(p => p!.x)).toEqual([0, 50, 100]);
    expect(points[2]!.y).toBeLessThan(points[1]!.y);
    expect(points[1]!.y).toBeLessThan(points[0]!.y);
  });

  it('draws a quiet route low instead of full height', () => {
    const quiet = sparkPoints([0, 0.2], 1);
    const busy = sparkPoints([0, 20], 1);
    expect(quiet[1]!.y).toBeGreaterThan(busy[1]!.y);
  });

  it('breaks the line at gaps', () => {
    const { line, area } = sparkPaths([1, 2, null, 3, 4]);
    expect(line.match(/M/g)!.length).toBe(2);
    expect(area.match(/Z/g)!.length).toBe(2);
    expect(sparkPaths([null, null]).line).toBe('');
  });

  it('recognises a window that moved on by a minute', () => {
    expect(isShift([1, 2, 3, 4], [2, 3, 4, 9])).toBe(true);
    expect(isShift([1, 2, 3, 4], [1, 2, 3, 5])).toBe(false);
    expect(isShift([2, 2, 2], [2, 2, 2])).toBe(false);
  });

  it('summarises for screen readers', () => {
    expect(sparkSummary([0, 14, 12], [0, 1, 0], 'calls'))
      .toBe('Calls per minute, last 3 minutes: between 0 and 14, now 12; failures in 1 minute');
    expect(sparkSummary([null], [], 'calls')).toBe('No calls recorded yet');
  });

  it('renders the line, a tick per failing minute, and its summary as the label', async () => {
    const fixture = TestBed.createComponent(SparklineComponent);
    fixture.componentRef.setInput('values', [1, 2, 3]);
    fixture.componentRef.setInput('failures', [0, 2, 0]);
    fixture.detectChanges();
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.qtt-sparkline__line')!.getAttribute('d')).toContain('M');
    expect(el.querySelectorAll('.qtt-sparkline__fail').length).toBe(1);
    expect(el.getAttribute('aria-label')).toContain('failures in 1 minute');
    expect(el.getAttribute('role')).toBe('img');
  });

  it('shows a dashed baseline without data', () => {
    const fixture = TestBed.createComponent(SparklineComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.qtt-sparkline__none')).not.toBeNull();
    expect(fixture.nativeElement.classList).toContain('is-empty');
  });
});
