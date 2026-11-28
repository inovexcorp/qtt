import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { DialogRun, RouteActionDialogComponent, RouteActionDialogData } from './route-action-dialog.component';

describe('RouteActionDialogComponent', () => {
  let close: ReturnType<typeof vi.fn>;
  let perform: ReturnType<typeof vi.fn>;

  function create(data: Partial<RouteActionDialogData>) {
    close = vi.fn();
    TestBed.configureTestingModule({
      imports: [RouteActionDialogComponent],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { action: 'stop', targets: [], perform, ...data } },
        { provide: MatDialogRef, useValue: { close, disableClose: false } },
      ],
    });
    const fixture = TestBed.createComponent(RouteActionDialogComponent);
    fixture.detectChanges();
    return fixture;
  }

  const text = (fixture: { nativeElement: HTMLElement }) => fixture.nativeElement.textContent!.replace(/\s+/g, ' ');
  const buttons = (fixture: { nativeElement: HTMLElement }) =>
    [...fixture.nativeElement.querySelectorAll('button')] as HTMLButtonElement[];
  const settle = () => new Promise(resolve => setTimeout(resolve));

  it('asks about one route, and warns when it is in use', () => {
    perform = vi.fn();
    const fixture = create({ targets: [{ id: 'people', perMinute: 12 }] });
    expect(text(fixture)).toContain('Stop people?');
    expect(text(fixture)).toContain('It is in use: 12/min');
    expect(buttons(fixture)[1].textContent!.trim()).toBe('Stop route');
  });

  it('lists the routes of a bulk delete, flagging the busy ones', () => {
    perform = vi.fn();
    const fixture = create({ action: 'delete', targets: [{ id: 'a', perMinute: 3 }, { id: 'b' }, { id: 'c', perMinute: 0 }] });
    expect(text(fixture)).toContain('Delete 3 routes?');
    expect(fixture.nativeElement.querySelectorAll('.qtt-dialog__chips li').length).toBe(3);
    expect(fixture.nativeElement.querySelectorAll('li.is-busy').length).toBe(1);
    expect(text(fixture)).toContain('1 of them is in use');
  });

  it('runs the action and closes with what was done', async () => {
    perform = vi.fn(async (ids: string[]): Promise<DialogRun> => ({ done: ids, failed: [] }));
    const fixture = create({ targets: [{ id: 'a' }] });
    buttons(fixture)[1].click();
    await settle();
    expect(perform).toHaveBeenCalledWith(['a']);
    expect(close).toHaveBeenCalledWith({ done: ['a'], failed: [] });
  });

  it('shows what failed and retries only those, then reports everything done', async () => {
    perform = vi.fn()
      .mockResolvedValueOnce({ done: ['a'], failed: [{ id: 'b', message: 'locked' }] })
      .mockResolvedValueOnce({ done: ['b'], failed: [] });
    const fixture = create({ action: 'delete', targets: [{ id: 'a' }, { id: 'b' }] });

    buttons(fixture)[1].click();
    await settle();
    fixture.detectChanges();
    expect(close).not.toHaveBeenCalled();
    expect(text(fixture)).toContain("Couldn't delete 1 of 2.");
    expect(text(fixture)).toContain('b: locked');
    expect(buttons(fixture)[0].textContent!.trim()).toBe('Close');
    expect(buttons(fixture)[1].textContent!.trim()).toBe('Try again');

    buttons(fixture)[1].click();
    await settle();
    expect(perform).toHaveBeenLastCalledWith(['b']);
    expect(close).toHaveBeenCalledWith({ done: ['a', 'b'], failed: [] });
  });

  it('closes with nothing when cancelled before anything ran', () => {
    perform = vi.fn();
    const fixture = create({ targets: [{ id: 'a' }] });
    buttons(fixture)[0].click();
    expect(close).toHaveBeenCalledWith(undefined);
    expect(perform).not.toHaveBeenCalled();
  });
});
