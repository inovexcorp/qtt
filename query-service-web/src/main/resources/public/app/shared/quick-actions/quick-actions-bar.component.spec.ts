import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { QuickActionsBarComponent } from './quick-actions-bar.component';
import { QuickActionsService } from './quick-actions.service';

describe('QuickActionsBarComponent', () => {
  let fixture: ComponentFixture<QuickActionsBarComponent>;
  let component: QuickActionsBarComponent;
  let http: HttpTestingController;
  let router: Router;
  let input: HTMLInputElement;

  const options = () => [...document.querySelectorAll<HTMLElement>('.qa-option')];
  const labels = () => options().map(o => o.querySelector('.qa-option-label')!.textContent!.trim());
  const groupLabels = () => [...document.querySelectorAll('.qa-group-label')].map(g => g.textContent!.trim());
  const press = (key: string) => {
    input.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
    fixture.detectChanges();
  };
  const type = (text: string) => {
    input.value = text;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };
  const answerEntities = () => {
    http.expectOne('/queryrest/api/routes/').flush([
      { routeId: 'people-search', description: 'Find people', status: 'Started', datasources: { dataSourceId: 'anzo' } }
    ]);
    http.expectOne('/queryrest/api/datasources/').flush([{ dataSourceId: 'anzo', url: 'http://anzo', status: 'UP' }]);
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [QuickActionsBarComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    fixture = TestBed.createComponent(QuickActionsBarComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();
    input = fixture.nativeElement.querySelector('input');
    document.body.appendChild(fixture.nativeElement);
  });

  afterEach(() => fixture.nativeElement.remove());

  it('is a closed combobox until focused', () => {
    expect(input.getAttribute('role')).toBe('combobox');
    expect(input.getAttribute('aria-expanded')).toBe('false');
    expect(options()).toEqual([]);
    http.expectNone('/queryrest/api/routes/');
  });

  it('opens on focus with the places to go, and loads routes and datasources', () => {
    input.focus();
    fixture.detectChanges();
    answerEntities();
    expect(input.getAttribute('aria-expanded')).toBe('true');
    // Signed-in state unknown in the test: every role, sign-in off (so no "Sign out").
    expect(groupLabels()).toEqual(['Go to', 'Actions']);
    expect(labels()).toEqual(['Metrics', 'Routes', 'Datasources', 'Settings',
      'Add route', 'Add datasource', 'Create API key', 'Clear all caches']);
    expect(input.getAttribute('aria-activedescendant')).toBe(options()[0].id);
  });

  it('filters as you type, highlights the match, and opens a route with Enter', () => {
    input.focus();
    fixture.detectChanges();
    answerEntities();
    type('peop');
    expect(groupLabels()).toEqual(['Routes']);
    expect(labels()).toEqual(['people-search']);
    expect(options()[0].querySelector('mark')!.textContent).toBe('peop');
    expect(options()[0].querySelector('.qa-option-detail')!.textContent).toContain('Find people');
    press('Enter');
    expect(router.navigate).toHaveBeenCalledWith(['/routes/config-route', 'people-search']);
    expect(input.getAttribute('aria-expanded')).toBe('false');
    expect(input.value).toBe('');
  });

  it('moves with the arrow keys, wrapping around', () => {
    input.focus();
    fixture.detectChanges();
    answerEntities();
    const count = options().length;
    press('ArrowDown');
    expect(input.getAttribute('aria-activedescendant')).toBe(options()[1].id);
    expect(options()[1].getAttribute('aria-selected')).toBe('true');
    press('ArrowUp');
    press('ArrowUp');
    expect(input.getAttribute('aria-activedescendant')).toBe(options()[count - 1].id);
    press('ArrowDown');
    press('Enter');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/metrics');
  });

  it('runs a clicked row', () => {
    input.focus();
    fixture.detectChanges();
    answerEntities();
    options()[1].click();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/routes');
  });

  it('shows a disabled action with its reason and does not run it', () => {
    const run = vi.fn();
    TestBed.inject(QuickActionsService).register([
      { id: 'x', label: 'Run test request', icon: 'play_arrow', group: 'page', run, disabledReason: () => 'Datasource is down' }
    ]);
    input.focus();
    fixture.detectChanges();
    answerEntities();
    expect(groupLabels()[0]).toBe('This page');
    expect(options()[0].getAttribute('aria-disabled')).toBe('true');
    expect(options()[0].textContent).toContain('Datasource is down');
    press('Enter');
    expect(run).not.toHaveBeenCalled();
    expect(input.getAttribute('aria-expanded')).toBe('true');
  });

  it('says when nothing matches, and announces the result count', () => {
    input.focus();
    fixture.detectChanges();
    answerEntities();
    type('zzzz');
    expect(document.querySelector('.qa-empty')!.textContent).toContain('No matches for “zzzz”');
    expect(fixture.nativeElement.querySelector('[aria-live]').textContent).toBe('No results');
    type('metr');
    expect(fixture.nativeElement.querySelector('[aria-live]').textContent).toBe('1 result');
  });

  it('opens from a shortcut and gives focus back on Escape', async () => {
    const before = document.createElement('button');
    document.body.appendChild(before);
    before.focus();
    component.openFromShortcut();
    fixture.detectChanges();
    await new Promise(resolve => setTimeout(resolve));
    expect(document.activeElement).toBe(input);
    answerEntities();
    press('Escape');
    expect(input.getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(before);
    before.remove();
  });

  it('closes on Tab', () => {
    input.focus();
    fixture.detectChanges();
    answerEntities();
    press('Tab');
    expect(input.getAttribute('aria-expanded')).toBe('false');
  });
});
