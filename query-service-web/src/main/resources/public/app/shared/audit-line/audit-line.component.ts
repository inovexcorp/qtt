import { Component, Input } from '@angular/core';
import { AuditInfo } from '../../core/models/audit';

/** One quiet line: "Created by alice on … · Last changed by bob on …". Renders nothing without audit data. */
@Component({
    selector: 'app-audit-line',
    template: `
    <p class="audit-line" *ngIf="audit && (audit.createdBy || audit.modifiedBy)">
      <ng-container *ngIf="audit.createdBy">Created by <strong>{{ audit.createdBy }}</strong>
        <span *ngIf="audit.createdAt"> on {{ audit.createdAt | date:'medium' }}</span></ng-container>
      <ng-container *ngIf="audit.createdBy && audit.modifiedBy"> · </ng-container>
      <ng-container *ngIf="audit.modifiedBy">Last changed by <strong>{{ audit.modifiedBy }}</strong>
        <span *ngIf="audit.modifiedAt"> on {{ audit.modifiedAt | date:'medium' }}</span></ng-container>
    </p>`,
    styles: [`.audit-line { margin: 4px 0 8px; font-size: 12px; color: rgba(0, 0, 0, 0.6); }`],
    standalone: false
})
export class AuditLineComponent {
  @Input() audit?: AuditInfo | null;
}
