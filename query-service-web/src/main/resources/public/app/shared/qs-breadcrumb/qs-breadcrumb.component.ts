import { Component, ChangeDetectionStrategy } from '@angular/core';
import { BreadcrumbComponent } from 'xng-breadcrumb';
import { MatToolbar } from '@angular/material/toolbar';

/**
 * The toolbar breadcrumb, labelled from route data. Pages showing one datasource or route label their crumb with its
 * name (see shared/breadcrumb-label.ts).
 */
@Component({
    selector: 'app-qs-breadcrumb',
    templateUrl: './qs-breadcrumb.component.html',
    styleUrls: ['./qs-breadcrumb.component.scss'],
    changeDetection: ChangeDetectionStrategy.Eager,
    imports: [MatToolbar, BreadcrumbComponent]
})
export class QsBreadcrumbComponent {
}
