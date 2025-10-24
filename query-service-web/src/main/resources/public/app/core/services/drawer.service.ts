import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class DrawerService {
  private closeDrawerSubject = new Subject<void>();

  closeDrawer$ = this.closeDrawerSubject.asObservable();

  requestCloseDrawer(): void {
    this.closeDrawerSubject.next();
  }
}
