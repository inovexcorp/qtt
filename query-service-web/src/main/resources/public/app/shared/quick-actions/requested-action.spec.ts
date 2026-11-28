import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { onRequestedAction } from './requested-action';

let runs = 0;

@Component({ template: 'page', changeDetection: ChangeDetectionStrategy.OnPush })
class Page {
  constructor() {
    onRequestedAction({ add: 'route' }, () => runs++);
  }
}

describe('onRequestedAction', () => {
  const settle = () => new Promise(resolve => setTimeout(resolve, 20));

  beforeEach(() => {
    runs = 0;
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: 'page', component: Page }])] });
  });

  it('runs once when the page opens with the parameter, and removes it from the URL', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/page?add=route&keep=1');
    await settle();
    expect(runs).toBe(1);
    expect(TestBed.inject(Router).url).toBe('/page?keep=1');
  });

  it('runs again when asked again on the open page, and not without the parameter', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/page');
    await settle();
    expect(runs).toBe(0);
    await harness.navigateByUrl('/page?add=route');
    await settle();
    expect(runs).toBe(1);
    await harness.navigateByUrl('/page?add=route');
    await settle();
    expect(runs).toBe(2);
    expect(TestBed.inject(Router).url).toBe('/page');
  });
});
