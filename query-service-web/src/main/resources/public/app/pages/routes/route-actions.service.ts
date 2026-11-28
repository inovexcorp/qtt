import { inject, Injectable } from '@angular/core';
import { Clipboard } from '@angular/cdk/clipboard';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { firstValueFrom, Observable } from 'rxjs';
import { saveErrorMessage } from '../../core/http-error-message';
import { RoutesService } from './routes.service';
import { DialogRun, RouteActionDialogComponent, RouteActionDialogData } from './route-action-dialog.component';

/** A route an action applies to, with what the confirmations and the start check need to know about it. */
export interface RouteTarget {
  id: string;
  /** Why it can't be started now, or null (see startBlockedReason). */
  startBlocked?: string | null;
  /** Its recent calls per minute, to warn before stopping or deleting a route that is in use. */
  perMinute?: number | null;
}

/** What a (bulk) action did. */
export interface ActionResult {
  done: string[];
  failed: { id: string; message: string }[];
  /** Not attempted (start only: the datasource is down or disabled). */
  skipped: { id: string; message: string }[];
}

/** How many requests a bulk action keeps in flight. */
export const BULK_CONCURRENCY = 4;

const ERROR_SNACK = { duration: 10000, panelClass: ['error-snackbar'] };

/**
 * Starting, stopping, cloning, deleting and copying routes, one way for every place that offers them (the routes
 * list and its bulk bar, a route's page, the quick actions bar). Stopping and deleting ask first, in a dialog that
 * runs them and shows what failed; every action ends with one message saying what happened. Bulk actions run a few requests at a time (the server has no bulk API).
 *
 * The callers refresh their own data afterwards (the routes list also sees the change on its live stream).
 */
@Injectable({ providedIn: 'root' })
export class RouteActionsService {
  private readonly routes = inject(RoutesService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly clipboard = inject(Clipboard);

  /** Starts routes; those whose datasource can't serve them are skipped (the server would refuse them). */
  async start(targets: RouteTarget[]): Promise<ActionResult> {
    const skipped = targets.filter(t => t.startBlocked).map(t => ({ id: t.id, message: t.startBlocked! }));
    const result = await this.run(targets.filter(t => !t.startBlocked), id => this.routes.updateRouteStatus(id, 'Started'));
    result.skipped = skipped;
    this.report(result, { verb: 'started', failVerb: 'start' });
    return result;
  }

  /** Asks, then stops routes (in the dialog, which shows failures). Null when the user cancels. */
  stop(targets: RouteTarget[]): Promise<ActionResult | null> {
    return this.confirmAndRun('stop', targets, id => this.routes.updateRouteStatus(id, 'Stopped'));
  }

  /** Asks, then deletes routes (in the dialog, which shows failures). Null when the user cancels. */
  delete(targets: RouteTarget[]): Promise<ActionResult | null> {
    return this.confirmAndRun('delete', targets, id => this.routes.deleteRoute(id));
  }

  /** Copies a route (to the first free "<id>Clone" id); the copy's id, or null when it failed. */
  async clone(routeId: string): Promise<string | null> {
    try {
      const { routeId: copy } = await firstValueFrom(this.routes.cloneRoute(routeId));
      this.snackBar.open(`Copied ${routeId} to ${copy}.`, 'Close', { duration: 5000 });
      return copy;
    } catch (error) {
      this.snackBar.open(`${routeId} not copied: ${saveErrorMessage(error)}`, 'Close', ERROR_SNACK);
      return null;
    }
  }

  /** Copies text (an endpoint URL) to the clipboard and says so. */
  copy(text: string, what = 'Endpoint URL'): void {
    if (this.clipboard.copy(text)) {
      this.snackBar.open(`${what} copied.`, undefined, { duration: 2500 });
    } else {
      this.snackBar.open(`Couldn't copy the ${what.toLowerCase()}.`, 'Close', ERROR_SNACK);
    }
  }

  // --- Confirmations ------------------------------------------------------------------------------------------------

  /**
   * Opens the confirmation, which runs the requests itself (progress, failures, retry). Says what was done once it
   * closes; failures were shown in the dialog.
   */
  private async confirmAndRun(action: 'stop' | 'delete', targets: RouteTarget[],
                              request: (id: string) => Observable<unknown>): Promise<ActionResult | null> {
    if (!targets.length) {
      return null;
    }
    const data: RouteActionDialogData = {
      action,
      targets,
      perform: ids => this.run(targets.filter(t => ids.includes(t.id)), request),
    };
    const run = await firstValueFrom(this.dialog.open<RouteActionDialogComponent, RouteActionDialogData, DialogRun>(
      RouteActionDialogComponent, { data, width: '480px', panelClass: 'qtt-dialog-panel', autoFocus: 'dialog' })
      .afterClosed(), { defaultValue: undefined });
    if (!run) {
      return null;
    }
    const verb = action === 'stop' ? 'stopped' : 'deleted';
    if (run.done.length) {
      this.snackBar.open(run.done.length === 1 ? `${run.done[0]} ${verb}.` : `${run.done.length} routes ${verb}.`,
        'Close', { duration: 4000 });
    }
    return { done: run.done, failed: run.failed, skipped: [] };
  }

  // --- Running and reporting ----------------------------------------------------------------------------------------

  /** Runs one request per route, at most {@link BULK_CONCURRENCY} at a time, collecting what worked. */
  private async run(targets: RouteTarget[], request: (id: string) => Observable<unknown>): Promise<ActionResult> {
    const result: ActionResult = { done: [], failed: [], skipped: [] };
    const queue = [...targets];
    const worker = async () => {
      for (let target = queue.shift(); target; target = queue.shift()) {
        try {
          await firstValueFrom(request(target.id), { defaultValue: null });
          result.done.push(target.id);
        } catch (error) {
          result.failed.push({ id: target.id, message: saveErrorMessage(error) });
        }
      }
    };
    await Promise.all(Array.from({ length: Math.min(BULK_CONCURRENCY, targets.length) }, worker));
    // In the order asked, not the order answered
    const order = new Map(targets.map((t, i) => [t.id, i]));
    result.done.sort((a, b) => order.get(a)! - order.get(b)!);
    result.failed.sort((a, b) => order.get(a.id)! - order.get(b.id)!);
    return result;
  }

  /** One message for the whole action: "people-search stopped.", "3 routes started; 1 skipped: …; 1 failed: …". */
  private report(result: ActionResult, words: { verb: string; failVerb: string }): void {
    const { done, failed, skipped } = result;
    const total = done.length + failed.length + skipped.length;
    if (total === 1) {
      if (done.length) {
        this.snackBar.open(`${done[0]} ${words.verb}.`, 'Close', { duration: 4000 });
      } else {
        const problem = failed[0] ?? skipped[0];
        this.snackBar.open(failed.length ? `${problem.id} not ${words.verb}: ${problem.message}` : problem.message,
          'Close', ERROR_SNACK);
      }
      return;
    }
    const parts = [`${done.length} ${done.length === 1 ? 'route' : 'routes'} ${words.verb}`];
    if (skipped.length) {
      parts.push(`${skipped.length} skipped (datasource down or disabled): ${skipped.map(s => s.id).join(', ')}`);
    }
    if (failed.length) {
      parts.push(`couldn't ${words.failVerb} ${failed.map(f => f.id).join(', ')}: ${failed[0].message}`);
    }
    const problems = failed.length + skipped.length > 0;
    this.snackBar.open(parts.join('; ') + '.', 'Close', problems ? ERROR_SNACK : { duration: 5000 });
  }
}
