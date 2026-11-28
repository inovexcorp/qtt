import { QttRole } from '../../core/services/auth.service';

/**
 * Where an action is listed: "page" for actions of the page on screen, "actions" for ones available everywhere,
 * "navigation" for going to a page.
 */
export type QuickActionGroup = 'page' | 'actions' | 'navigation';

/** Something the quick actions bar can run. */
export interface QuickAction {
  /** Unique among registered actions; a later registration with the same id replaces the earlier one. */
  id: string;
  label: string;
  /** Material icon name. */
  icon: string;
  group: QuickActionGroup;
  /** Other words that find it (matched without highlighting), e.g. "start" for "Turn route on". */
  keywords?: string[];
  /** Hidden from users without this role (cosmetic: the server enforces roles). Default: viewer. */
  minRole?: QttRole;
  /** Shown at the end of the row, e.g. the page it goes to. */
  hint?: string;
  /** Listed only once something is typed (keeps the list short when the bar opens). */
  searchOnly?: boolean;
  /** Asked each time the bar opens; false hides the action (e.g. "Turn on" while the route runs). */
  visible?: () => boolean;
  /** Asked each time the bar opens; a reason shows the action greyed out with the reason, and it can't be run. */
  disabledReason?: () => string | null;
  run: () => void | Promise<unknown>;
}

/** A route as listed by the bar. */
export interface RouteEntry {
  routeId: string;
  description: string;
  status: string;
  dataSourceId: string;
}

/** A datasource as listed by the bar. */
export interface DatasourceEntry {
  dataSourceId: string;
  url: string;
  status?: string;
}
