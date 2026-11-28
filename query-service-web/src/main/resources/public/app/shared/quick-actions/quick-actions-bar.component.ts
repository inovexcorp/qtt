import {
  ChangeDetectionStrategy, Component, DOCUMENT, ElementRef, OnDestroy, OnInit, ViewChild, computed, inject, signal
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { BreakpointObserver } from '@angular/cdk/layout';
import { CdkConnectedOverlay, CdkOverlayOrigin, ConnectedPosition } from '@angular/cdk/overlay';
import { MatIcon } from '@angular/material/icon';
import { MatIconButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../core/services/auth.service';
import { CacheService } from '../../core/services/cache.service';
import { Subscription, map } from 'rxjs';
import { QuickActionsService } from './quick-actions.service';
import { EntityIndexService } from './entity-index.service';
import { GlobalShortcutsService } from './global-shortcuts.service';
import { globalActions, navigationActions, openShortcuts } from './global-actions';
import { RecentsService } from './recents.service';
import {
  GROUP_LABELS, ResultGroup, ResultItem, SCOPE_LABELS, SCOPE_PREFIXES, Scope, flatten, search
} from './search';
import { Segment, segments } from './match';

/** Below this width the field collapses to a search button. */
export const NARROW_QUERY = '(max-width: 719.98px)';

/** Shown when the query is "?": the prefixes, and the full shortcut list. */
const HELP_GROUP: ResultGroup = {
  id: 'help',
  label: GROUP_LABELS.help,
  more: 0,
  items: [
    ...(Object.entries(SCOPE_PREFIXES) as [string, Scope][]).map(([prefix, scope]) => ({
      key: 'help:' + scope, kind: 'help' as const, label: `Search ${SCOPE_LABELS[scope].toLowerCase()}`, ranges: [],
      icon: scope === 'actions' ? 'bolt' : scope === 'routes' ? 'dashboard' : 'dns', hint: prefix,
      helpScope: scope, score: 0
    })),
    { key: 'help:shortcuts', kind: 'help', label: 'Keyboard shortcuts', ranges: [], icon: 'keyboard', hint: '?',
      helpScope: null, score: 0 }
  ]
};

/**
 * The quick actions bar: a search field in the toolbar that finds pages, routes, datasources and actions, and runs
 * them. Opens on click, "/" or Cmd/Ctrl+K. Follows the ARIA combobox pattern: focus stays in the field, and the
 * highlighted option is announced through aria-activedescendant.
 */
@Component({
  selector: 'app-quick-actions-bar',
  templateUrl: './quick-actions-bar.component.html',
  styleUrls: ['./quick-actions-bar.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CdkOverlayOrigin, CdkConnectedOverlay, MatIcon, MatIconButton],
  host: {
    '[class.narrow]': 'narrow()',
    '[class.open]': 'open()'
  }
})
export class QuickActionsBarComponent implements OnInit, OnDestroy {

  private readonly router = inject(Router);
  private readonly registry = inject(QuickActionsService);
  readonly entities = inject(EntityIndexService);
  private readonly recents = inject(RecentsService);
  private readonly shortcuts = inject(GlobalShortcutsService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly document = inject(DOCUMENT);
  private readonly globalDeps = {
    router: this.router, auth: inject(AuthService), dialog: inject(MatDialog), snackBar: inject(MatSnackBar),
    cache: inject(CacheService)
  };

  @ViewChild('input', { static: true }) input!: ElementRef<HTMLInputElement>;

  readonly query = signal('');
  /** Set by typing ">", "#" or "@" first; shown as a chip in the field, removed with Backspace. */
  readonly scope = signal<Scope | null>(null);
  readonly scopeLabels = SCOPE_LABELS;
  readonly open = signal(false);
  readonly narrow = toSignal(inject(BreakpointObserver).observe(NARROW_QUERY).pipe(map(s => s.matches)),
    { initialValue: false });

  /** Bumped on each opening, so actions' visible()/disabledReason() are asked again. */
  private readonly revision = signal(0);
  private readonly activeIndex = signal(0);

  readonly groups = computed<ResultGroup[]>(() => {
    this.revision();
    if (!this.scope() && this.query().trim() === '?') {
      return [HELP_GROUP];
    }
    const current = this.recents.current();
    return search({
      query: this.query(),
      scope: this.scope(),
      actions: this.registry.actions(),
      routes: this.entities.routes(),
      datasources: this.entities.datasources(),
      recents: this.recents.entries().filter(e => e.kind !== current?.kind || e.id !== current?.id),
      entitiesLoaded: this.entities.loaded()
    });
  });
  readonly placeholder = computed(() => {
    switch (this.scope()) {
      case 'actions': return 'Search actions…';
      case 'routes': return 'Search routes…';
      case 'datasources': return 'Search datasources…';
      default: return 'Search or jump to…';
    }
  });
  readonly items = computed(() => flatten(this.groups()));
  readonly active = computed<ResultItem | null>(() => {
    const items = this.items();
    return items.length ? items[Math.min(this.activeIndex(), items.length - 1)] : null;
  });
  readonly activeId = computed(() => {
    const active = this.active();
    return this.open() && active ? this.optionId(active) : null;
  });
  /** Read out by screen readers when the results change. */
  readonly announcement = computed(() => {
    if (!this.open()) {
      return '';
    }
    const n = this.items().length;
    return n === 0 ? 'No results' : n === 1 ? '1 result' : `${n} results`;
  });

  readonly positions: ConnectedPosition[] = [
    { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 6 },
    { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 6 }
  ];

  /** Where focus was before a shortcut opened the bar; Escape puts it back. */
  private returnFocus: HTMLElement | null = null;
  private subscription?: Subscription;
  private unregister?: () => void;

  ngOnInit(): void {
    this.unregister = this.registry.register([...globalActions(this.globalDeps), ...navigationActions(this.router)]);
    this.shortcuts.start();
    this.subscription = this.shortcuts.shortcut$.subscribe(shortcut => {
      if (shortcut === 'open') {
        this.openFromShortcut();
      } else {
        this.close(false);
        openShortcuts(this.globalDeps.dialog);
      }
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
    this.unregister?.();
  }

  /** Cmd/Ctrl+K or "/": remember where focus was, open, and focus the field. */
  openFromShortcut(): void {
    const focused = this.document.activeElement;
    if (focused instanceof HTMLElement && focused !== this.input.nativeElement && focused !== this.document.body) {
      this.returnFocus = focused;
    }
    this.show();
    this.focusInput();
  }

  /** The search button on narrow screens. */
  openFromButton(): void {
    this.show();
    this.focusInput();
  }

  onFocus(): void {
    this.show();
  }

  onInput(value: string): void {
    const scope = this.scope() ? null : SCOPE_PREFIXES[value.charAt(0)];
    if (scope) {
      this.scope.set(scope);
      value = value.slice(1);
      this.input.nativeElement.value = value;
    }
    this.query.set(value);
    this.activeIndex.set(0);
    this.show();
  }

  onKeydown(event: KeyboardEvent): void {
    switch (event.key) {
      case 'ArrowDown':
      case 'ArrowUp': {
        event.preventDefault();
        if (!this.open()) {
          this.show();
          return;
        }
        this.move(event.key === 'ArrowDown' ? 1 : -1);
        return;
      }
      case 'Enter': {
        event.preventDefault();
        const active = this.active();
        if (this.open() && active) {
          this.execute(active);
        }
        return;
      }
      case 'Escape': {
        event.preventDefault();
        this.close(true);
        return;
      }
      case 'Tab':
        this.close(false);
        return;
      case 'Backspace': {
        const field = this.input.nativeElement;
        if (this.scope() && field.selectionStart === 0 && field.selectionEnd === 0) {
          event.preventDefault();
          this.scope.set(null);
          this.activeIndex.set(0);
        }
        return;
      }
    }
  }

  /** Clicks outside the panel close it, except clicks on the field itself. */
  onOutsideClick(event: MouseEvent): void {
    if (event.target instanceof Node && this.host.nativeElement.contains(event.target)) {
      return;
    }
    this.close(false);
  }

  setActive(item: ResultItem): void {
    const index = this.items().indexOf(item);
    if (index >= 0 && index !== this.activeIndex()) {
      this.activeIndex.set(index);
    }
  }

  /** Runs a row: opens the route or datasource, or runs the action. Disabled rows do nothing. */
  execute(item: ResultItem): void {
    if (item.disabledReason) {
      return;
    }
    if (item.kind === 'help') {
      this.query.set('');
      this.input.nativeElement.value = '';
      if (item.helpScope) {
        this.scope.set(item.helpScope);
        this.activeIndex.set(0);
      } else {
        this.close(false);
        openShortcuts(this.globalDeps.dialog);
      }
      return;
    }
    this.close(false);
    let result: unknown;
    if (item.kind === 'route') {
      result = this.router.navigate(['/routes/config-route', item.entityId]);
    } else if (item.kind === 'datasource') {
      result = this.router.navigate(['/datasources/config-datasource', item.entityId]);
    } else {
      result = item.action?.run();
    }
    if (result instanceof Promise) {
      result.catch(error => console.error('Quick action failed:', error));
    }
  }

  close(restoreFocus: boolean): void {
    if (!this.open()) {
      if (restoreFocus) {
        this.input.nativeElement.blur();
      }
      return;
    }
    // Each opening starts afresh: no leftover text or narrowing in the closed field.
    this.open.set(false);
    this.scope.set(null);
    this.query.set('');
    this.input.nativeElement.value = '';
    const back = this.returnFocus;
    this.returnFocus = null;
    if (restoreFocus && back?.isConnected) {
      back.focus();
    } else {
      this.input.nativeElement.blur();
    }
  }

  optionId(item: ResultItem): string {
    return 'qa-option-' + item.key.replace(/[^a-zA-Z0-9_-]/g, '_');
  }

  segments(item: ResultItem): Segment[] {
    return segments(item.label, item.ranges);
  }

  private show(): void {
    if (this.open()) {
      return;
    }
    this.revision.update(r => r + 1);
    this.activeIndex.set(0);
    this.entities.refresh();
    this.open.set(true);
  }

  /**
   * Focuses the field at once, so keys typed right after the shortcut land in it. On narrow screens the field is
   * hidden until the "open" class is applied, so focus then waits for the next tick.
   */
  private focusInput(): void {
    const field = this.input.nativeElement;
    field.focus();
    if (this.document.activeElement !== field) {
      setTimeout(() => field.focus());
    }
  }

  private move(step: number): void {
    const count = this.items().length;
    if (!count) {
      return;
    }
    const current = Math.min(this.activeIndex(), count - 1);
    this.activeIndex.set((current + step + count) % count);
    const active = this.active();
    if (active) {
      setTimeout(() => this.document.getElementById(this.optionId(active))?.scrollIntoView({ block: 'nearest' }));
    }
  }
}
