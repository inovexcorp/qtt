import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Clipboard } from '@angular/cdk/clipboard';
import { from, of } from 'rxjs';
import { BULK_CONCURRENCY, RouteActionsService } from './route-actions.service';

const settle = () => new Promise(resolve => setTimeout(resolve, 0));

describe('RouteActionsService', () => {
  let actions: RouteActionsService;
  let http: HttpTestingController;
  let answer: boolean;
  let dialogOpen: ReturnType<typeof vi.fn>;
  let snack: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    answer = true;
    // Confirming runs the action, as the real dialog does; cancelling closes with nothing
    dialogOpen = vi.fn((_: unknown, config: { data: { targets: { id: string }[]; perform(ids: string[]): Promise<unknown> } }) => ({
      afterClosed: () => answer ? from(config.data.perform(config.data.targets.map(t => t.id))) : of(undefined),
    }));
    snack = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(),
        { provide: MatDialog, useValue: { open: dialogOpen } },
        { provide: MatSnackBar, useValue: { open: snack } },
        { provide: Clipboard, useValue: { copy: () => true } },
      ],
    });
    actions = TestBed.inject(RouteActionsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts routes, skipping those whose datasource is down, and reports both', async () => {
    const done = actions.start([{ id: 'a' }, { id: 'b', startBlocked: "Can't start: datasource ds is down" }]);
    await settle();
    const req = http.expectOne('/queryrest/api/routes/a');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toBe('Started');
    req.flush(null);

    const result = await done;
    expect(result.done).toEqual(['a']);
    expect(result.skipped.map(s => s.id)).toEqual(['b']);
    expect(snack.mock.calls[0][0]).toContain('1 route started; 1 skipped');
    expect(dialogOpen).not.toHaveBeenCalled(); // starting doesn't ask
  });

  it('asks before stopping, and stops nothing when cancelled', async () => {
    answer = false;
    expect(await actions.stop([{ id: 'a', perMinute: 12 }])).toBeNull();
    expect(dialogOpen).toHaveBeenCalledTimes(1);
    const data = dialogOpen.mock.calls[0][1].data;
    expect(data.action).toBe('stop');
    expect(data.targets).toEqual([{ id: 'a', perMinute: 12 }]); // the dialog warns that it is in use
    http.expectNone('/queryrest/api/routes/a');
  });

  it('stops a route once confirmed and says so', async () => {
    const done = actions.stop([{ id: 'a' }]);
    await settle();
    http.expectOne(r => r.method === 'PATCH' && r.body === 'Stopped').flush(null);
    expect((await done)!.done).toEqual(['a']);
    expect(snack.mock.calls[0][0]).toBe('a stopped.');
  });

  it('reports the server\'s reason when a single action fails', async () => {
    const done = actions.start([{ id: 'a' }]);
    await settle();
    http.expectOne('/queryrest/api/routes/a').flush("Cannot start route 'a' - datasource 'ds' is currently DOWN",
      { status: 409, statusText: 'Conflict' });
    const result = await done;
    expect(result.failed).toEqual([{ id: 'a', message: "Cannot start route 'a' - datasource 'ds' is currently DOWN" }]);
    expect(snack.mock.calls[0][0]).toContain('a not started: Cannot start');
  });

  it(`keeps at most ${BULK_CONCURRENCY} requests in flight and reports in the order asked`, async () => {
    const ids = ['a', 'b', 'c', 'd', 'e', 'f'];
    const done = actions.delete(ids.map(id => ({ id })));
    await settle();
    let pending = http.match(r => r.method === 'DELETE');
    expect(pending.length).toBe(BULK_CONCURRENCY);
    // Answer out of order; one fails
    pending[2].flush('gone wrong', { status: 500, statusText: 'Error' });
    pending.reverse().filter((_, i) => i !== 1).forEach(r => r.flush(null));
    await settle();
    pending = http.match(r => r.method === 'DELETE');
    expect(pending.length).toBe(2);
    pending.forEach(r => r.flush(null));

    const result = (await done)!;
    expect(result.done).toEqual(['a', 'b', 'd', 'e', 'f']);
    expect(result.failed.map(f => f.id)).toEqual(['c']);
    expect(snack.mock.calls[0][0]).toBe('5 routes deleted.'); // the failure was shown in the dialog
    expect(dialogOpen.mock.calls[0][1].data.action).toBe('delete');
  });

  it('clones to the id the server picked', async () => {
    const done = actions.clone('a');
    http.expectOne('/queryrest/api/routes/clone/a').flush({ routeId: 'aClone2', endpointUrl: 'x' });
    expect(await done).toBe('aClone2');
    expect(snack.mock.calls[0][0]).toBe('Copied a to aClone2.');
  });
});
