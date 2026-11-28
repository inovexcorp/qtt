/*
 * Characterization tests for the third-party UI libraries, rendered for real (no NO_ERRORS_SCHEMA): ngx-markdown
 * (SPARQi chat), ngx-charts (Metrics, SPARQi metrics), xng-breadcrumb, and Monaco's keyboard handling next to the
 * quick actions bar. They pin what the app relies on so a library upgrade that changes rendering or inputs fails here
 * rather than in the browser.
 */
import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router, RouterOutlet } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MarkdownComponent, provideMarkdown } from 'ngx-markdown';
import { NgxChartsModule, ScaleType, Color } from '@swimlane/ngx-charts';
import { BreadcrumbComponent } from 'xng-breadcrumb';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import * as monaco from 'monaco-editor';
import { CodeEditorComponent } from './code-editor/code-editor.component';
import { QuickActionsBarComponent } from './quick-actions/quick-actions-bar.component';
import { breadcrumbLabel, ROUTE_CRUMB } from './breadcrumb-label';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';

const SPARQI_REPLY = [
  '## Suggested query',
  '',
  'Here is what I found:',
  '',
  '- first **bold** item',
  '- second `code` item',
  '',
  '1. one',
  '2. two',
  '',
  '```sparql',
  'SELECT ?s WHERE { ?s ?p ?o }',
  '```',
  '',
  '| Class | Count |',
  '|-------|------:|',
  '| Person | 3 |',
  '',
  'See [the docs](https://example.org/docs) for more.',
  '',
  '<script>window.__pwned = true</script><span onclick="window.__pwned = true">x</span>'
].join('\n');

@Component({
  imports: [MarkdownComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `<markdown [data]="text" class="markdown-content"></markdown>`
})
class MarkdownHost {
  text = SPARQI_REPLY;
}

describe('ngx-markdown rendering (SPARQi chat)', () => {
  let fixture: ComponentFixture<MarkdownHost>;
  let el: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [MarkdownHost], providers: [provideMarkdown()] }).compileComponents();
    fixture = TestBed.createComponent(MarkdownHost);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    el = fixture.nativeElement.querySelector('markdown');
  });

  it('renders headings, paragraphs, lists and inline formatting', () => {
    expect(el.querySelector('h2')?.textContent?.trim()).toBe('Suggested query');
    expect(el.querySelector('p')?.textContent?.trim()).toBe('Here is what I found:');
    const bullets = Array.from(el.querySelectorAll('ul > li')).map(li => li.textContent?.trim());
    expect(bullets).toEqual(['first bold item', 'second code item']);
    expect(el.querySelector('ul strong')?.textContent).toBe('bold');
    expect(el.querySelector('ul code')?.textContent).toBe('code');
    expect(el.querySelectorAll('ol > li').length).toBe(2);
  });

  it('renders fenced code blocks with their language', () => {
    const code = el.querySelector('pre > code');
    expect(code?.textContent?.trim()).toBe('SELECT ?s WHERE { ?s ?p ?o }');
    expect(code?.className).toContain('language-sparql');
  });

  it('renders tables and links', () => {
    expect(Array.from(el.querySelectorAll('table th')).map(th => th.textContent?.trim())).toEqual(['Class', 'Count']);
    expect(Array.from(el.querySelectorAll('table td')).map(td => td.textContent?.trim())).toEqual(['Person', '3']);
    const link = el.querySelector('a');
    expect(link?.getAttribute('href')).toBe('https://example.org/docs');
    expect(link?.textContent).toBe('the docs');
  });

  it('sanitizes HTML in the reply', () => {
    expect(el.querySelector('script')).toBeNull();
    expect(el.innerHTML).not.toContain('onclick');
    expect((window as any).__pwned).toBeUndefined();
  });
});

const PURPLE: Color = {
  name: 'purple', selectable: true, group: ScaleType.Ordinal,
  domain: ['#4a148c', '#6a1b9a', '#7b1fa2', '#8e24aa', '#9c27b0', '#ab47bc', '#ba68c8', '#ce93d8']
};

@Component({
  imports: [NgxChartsModule],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <ngx-charts-number-card id="cards" [results]="cards" [scheme]="scheme" [view]="[850,180]" cardColor="#4a148c">
    </ngx-charts-number-card>
    <ngx-charts-linear-gauge id="gauge" [value]="75" units="Average Success Rate" [view]="[350,150]">
    </ngx-charts-linear-gauge>
    <ngx-charts-bar-vertical-stacked id="stacked" [results]="series" [scheme]="scheme" [xAxis]="true" [yAxis]="true"
      [legend]="true" [view]="[700,300]"></ngx-charts-bar-vertical-stacked>
    <ngx-charts-bar-vertical-2d id="grouped" [results]="series" [scheme]="scheme" [xAxis]="true" [yAxis]="true"
      [view]="[700,300]"></ngx-charts-bar-vertical-2d>
    <ngx-charts-line-chart id="line" [results]="lines" [scheme]="scheme" [xAxis]="true" [yAxis]="true"
      [view]="[700,300]"></ngx-charts-line-chart>
    <ngx-charts-pie-chart id="pie" [results]="cards" [scheme]="scheme" [labels]="true" [view]="[400,300]">
    </ngx-charts-pie-chart>`
})
class ChartsHost {
  scheme = PURPLE;
  cards = [{ name: 'Total Exchanges', value: 12 }, { name: 'Routes', value: 3 }];
  series = [
    { name: 'route-a', series: [{ name: 'Completed', value: 10 }, { name: 'Failed', value: 2 }] },
    { name: 'route-b', series: [{ name: 'Completed', value: 4 }, { name: 'Failed', value: 1 }] }
  ];
  lines = [{ name: 'route-a', series: [{ name: '10:00', value: 5 }, { name: '10:05', value: 7 }, { name: '10:10', value: 6 }] }];
}

describe('ngx-charts rendering (Metrics)', () => {
  let root: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({ imports: [ChartsHost, NoopAnimationsModule] });
    const fixture = TestBed.createComponent(ChartsHost);
    fixture.detectChanges();
    await fixture.whenStable();
    await new Promise(resolve => setTimeout(resolve, 300)); // charts lay out after a timeout
    fixture.detectChanges();
    root = fixture.nativeElement;
  });

  const chart = (id: string) => root.querySelector(`#${id}`) as HTMLElement;

  it('renders number cards with their labels', () => {
    expect(chart('cards').querySelectorAll('g.cell').length).toBe(2);
    expect(chart('cards').textContent).toContain('Total Exchanges');
  });

  it('renders the linear gauge', () => {
    expect(chart('gauge').querySelector('svg')).not.toBeNull();
    expect(chart('gauge').textContent).toContain('Average Success Rate');
  });

  it('renders stacked and grouped bars, one per data point, in the scheme colors', () => {
    for (const id of ['stacked', 'grouped']) {
      const bars = chart(id).querySelectorAll('path.bar');
      expect(bars.length, id).toBe(4);
      expect(PURPLE.domain, id).toContain(bars[0].getAttribute('fill')!);
    }
    expect(chart('stacked').querySelector('.chart-legend')?.textContent).toContain('Completed');
  });

  it('renders line and pie charts', () => {
    expect(chart('line').querySelector('path.line')).not.toBeNull();
    expect(chart('pie').querySelectorAll('path.arc').length).toBe(2);
  });
});

@Component({
  selector: 'test-section', template: '<router-outlet></router-outlet>', changeDetection: ChangeDetectionStrategy.Eager,
  imports: [RouterOutlet]
})
class Section {
}

@Component({ standalone: true, selector: 'test-page', changeDetection: ChangeDetectionStrategy.Eager,
  template: 'page' })
class Page {
}

@Component({
  imports: [BreadcrumbComponent, RouterOutlet],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `<xng-breadcrumb separator="/"></xng-breadcrumb><router-outlet></router-outlet>`
})
class BreadcrumbHost {
}

/** A page named after what it shows, as the route and datasource pages are. */
@Component({ standalone: true, selector: 'test-named-page', changeDetection: ChangeDetectionStrategy.Eager, template: 'named' })
class NamedPage {
  // As the real pages do: the id is a signal, since the page is reused when only the id changes
  private readonly id = toSignal(inject(ActivatedRoute).paramMap.pipe(map(p => p.get('routeId') ?? '')), { initialValue: '' });
  constructor() {
    breadcrumbLabel(ROUTE_CRUMB, () => this.id());
  }
}

describe('xng-breadcrumb (app toolbar)', () => {
  // The same shape as app-routing.module.ts: labels come from route data.
  const routes = [
    { path: '', component: BreadcrumbHost, children: [
        { path: 'routes', component: Section, data: { breadcrumb: 'Routes' }, children: [
            { path: 'config-route/:routeId', component: Page, data: { breadcrumb: 'Config-Route' } }
          ] },
        { path: 'datasources', component: Section, data: { breadcrumb: 'DataSources' }, children: [
            { path: 'config-datasource/:dataSourceId', component: Page, data: { breadcrumb: 'Config-Datasource' } }
          ] },
        { path: 'metrics', component: Page, data: { breadcrumb: 'Metrics' } },
        { path: 'named', component: Section, data: { breadcrumb: 'Routes' }, children: [
            { path: ':routeId', component: NamedPage, data: { breadcrumb: { alias: ROUTE_CRUMB, label: 'Route' } } }
          ] }
      ] }
  ];

  const crumbs = (root: Element) => Array.from(root.querySelectorAll('.xng-breadcrumb-item')).map(i => i.textContent?.trim());

  beforeEach(() => TestBed.configureTestingModule({ providers: [provideRouter(routes)] }));

  it('shows one crumb per route level, labelled from route data', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/routes/config-route/r1');
    expect(crumbs(harness.routeNativeElement!)).toEqual(['Routes', 'Config-Route']);
    await harness.navigateByUrl('/metrics');
    expect(crumbs(harness.routeNativeElement!)).toEqual(['Metrics']);
  });

  it("labels a page's crumb with the name it shows, not the generic label", async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/named/people-search');
    harness.detectChanges();
    expect(crumbs(harness.routeNativeElement!)).toEqual(['Routes', 'people-search']);
    await harness.navigateByUrl('/named/org-lookup');
    harness.detectChanges();
    expect(crumbs(harness.routeNativeElement!)).toEqual(['Routes', 'org-lookup']);
  });

  it('links parent crumbs to their route and leaves the current one unlinked', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/datasources/config-datasource/d1');
    const items = harness.routeNativeElement!.querySelectorAll('.xng-breadcrumb-item');
    expect(items[0].querySelector('a')?.getAttribute('href')).toContain('/datasources');
    expect(items[1].querySelector('a')).toBeNull();
    expect(TestBed.inject(Router).url).toBe('/datasources/config-datasource/d1');
  });
});

@Component({
  imports: [CodeEditorComponent, QuickActionsBarComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `<app-quick-actions-bar></app-quick-actions-bar>
    <app-code-editor (editorInit)="editor = $event" style="display: block; height: 120px; width: 400px"></app-code-editor>`
})
class EditorWithBarHost {
  editor?: monaco.editor.IStandaloneCodeEditor;
}

describe('Monaco editor and the quick actions bar', () => {
  it('Cmd/Ctrl+K in the editor opens the bar; Monaco never sees the key (no chord starts)', async () => {
    await TestBed.configureTestingModule({
      imports: [EditorWithBarHost],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    const fixture = TestBed.createComponent(EditorWithBarHost);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
    const editor = fixture.componentInstance.editor!;
    const seenByMonaco: string[] = [];
    editor.onKeyDown(e => seenByMonaco.push(e.browserEvent.key));
    editor.focus();
    const target = document.activeElement!;
    expect(target.closest('.monaco-editor')).not.toBeNull();

    // A plain key reaches Monaco, so the listener above works.
    target.dispatchEvent(new KeyboardEvent('keydown', { key: 'a', bubbles: true, cancelable: true }));
    expect(seenByMonaco).toEqual(['a']);
    // "/" is typing in the editor: Monaco gets it, the bar stays closed.
    target.dispatchEvent(new KeyboardEvent('keydown', { key: '/', bubbles: true, cancelable: true }));
    expect(seenByMonaco).toEqual(['a', '/']);

    for (const init of [{ metaKey: true }, { ctrlKey: true }]) {
      target.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', bubbles: true, cancelable: true, ...init }));
    }
    fixture.detectChanges();
    await new Promise(resolve => setTimeout(resolve));
    expect(seenByMonaco).toEqual(['a', '/']);
    const input = fixture.nativeElement.querySelector('app-quick-actions-bar input') as HTMLInputElement;
    expect(document.activeElement).toBe(input);
    expect(input.getAttribute('aria-expanded')).toBe('true');
    TestBed.inject(HttpTestingController).match(() => true).forEach(r => r.flush([]));
    fixture.destroy();
    fixture.nativeElement.remove();
  });
});
