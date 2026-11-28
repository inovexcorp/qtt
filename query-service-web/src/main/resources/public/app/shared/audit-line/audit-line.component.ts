import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { AuditInfo } from '../../core/models/audit';

/** One quiet line: "Created by alice on … · Last changed by bob on …". Renders nothing without audit data. */
@Component({
    selector: 'app-audit-line',
    template: `
    @if (audit && (audit.createdBy || audit.modifiedBy)) {
      <p class="audit-line">
        @if (audit.createdBy) {
          Created by <strong>{{ audit.createdBy }}</strong>
          @if (audit.createdAt) {
            <span> on {{ audit.createdAt | date:'medium' }}</span>
          }
        }
        @if (audit.createdBy && audit.modifiedBy) {
          ·
        }
        @if (audit.modifiedBy) {
          Last changed by <strong>{{ audit.modifiedBy }}</strong>
          @if (audit.modifiedAt) {
            <span> on {{ audit.modifiedAt | date:'medium' }}</span>
          }
        }
      </p>
    }`,
    styles: [`.audit-line { margin: 4px 0 8px; font-size: 12px; color: rgba(0, 0, 0, 0.6); }`],
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
export class AuditLineComponent {
  @Input() audit?: AuditInfo | null;
}
