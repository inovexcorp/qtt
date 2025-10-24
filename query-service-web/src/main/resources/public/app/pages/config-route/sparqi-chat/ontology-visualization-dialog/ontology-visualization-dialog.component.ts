import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { OntologyService } from '../../../../core/services/ontology/ontology.service';
import { OntologyElement, OntologyElementType } from '../../../../core/models/ontology/ontology-element';
import { MatSnackBar } from '@angular/material/snack-bar';

interface GroupedElements {
  label: string;
  icon: string;
  count: number;
  elements: OntologyElement[];
}

interface ElementStatistics {
  classes: number;
  objectProperties: number;
  datatypeProperties: number;
  annotationProperties: number;
  individuals: number;
  total: number;
}

@Component({
  selector: 'app-ontology-visualization-dialog',
  templateUrl: './ontology-visualization-dialog.component.html',
  styleUrls: ['./ontology-visualization-dialog.component.scss']
})
export class OntologyVisualizationDialogComponent implements OnInit {
  routeId: string;
  elementCount: number;

  elements: OntologyElement[] = [];
  filteredElements: OntologyElement[] = [];
  groupedElements: GroupedElements[] = [];

  isLoading = false;
  error: string | null = null;
  searchTerm = '';
  selectedType: OntologyElementType = 'all';

  statistics: ElementStatistics = {
    classes: 0,
    objectProperties: 0,
    datatypeProperties: 0,
    annotationProperties: 0,
    individuals: 0,
    total: 0
  };

  constructor(
    public dialogRef: MatDialogRef<OntologyVisualizationDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { routeId: string, elementCount: number },
    private ontologyService: OntologyService,
    private snackBar: MatSnackBar
  ) {
    this.routeId = data.routeId;
    this.elementCount = data.elementCount;
  }

  ngOnInit(): void {
    this.loadOntologyElements();
  }

  /**
   * Load ontology elements from the API
   */
  loadOntologyElements(): void {
    this.isLoading = true;
    this.error = null;

    this.ontologyService.getOntologyElements({
      routeId: this.routeId,
      type: 'all',
      limit: 1000
    }).subscribe({
      next: (elements) => {
        this.elements = this.deduplicateElements(elements);
        this.filteredElements = this.elements;
        this.calculateStatistics();
        this.groupElements();
        this.isLoading = false;

        // Warn if limit reached
        if (elements.length >= 1000 && this.elementCount > 1000) {
          console.warn(`Displaying 1000 of ${this.elementCount} elements`);
          this.snackBar.open(`Showing first 1000 of ${this.elementCount} elements`, 'OK', {
            duration: 5000
          });
        }
      },
      error: (error) => {
        this.error = 'Failed to load ontology elements. Please try again.';
        this.isLoading = false;
        console.error('Failed to load ontology:', error);
      }
    });
  }

  /**
   * Remove duplicate elements based on URI and type
   */
  private deduplicateElements(elements: OntologyElement[]): OntologyElement[] {
    const seen = new Set<string>();
    return elements.filter(element => {
      const key = `${element.uri}:${element.type}`;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  }

  /**
   * Calculate statistics for element types
   */
  private calculateStatistics(): void {
    this.statistics = {
      classes: this.elements.filter(e => e.type === 'class').length,
      objectProperties: this.elements.filter(e => e.type === 'objectProperty').length,
      datatypeProperties: this.elements.filter(e => e.type === 'datatypeProperty').length,
      annotationProperties: this.elements.filter(e => e.type === 'annotationProperty').length,
      individuals: this.elements.filter(e => e.type === 'individual').length,
      total: this.elements.length
    };
  }

  /**
   * Group elements by type
   */
  private groupElements(): void {
    const typeGroups: Record<string, { label: string, icon: string, elements: OntologyElement[] }> = {
      'class': { label: 'Classes', icon: 'category', elements: [] },
      'objectProperty': { label: 'Object Properties', icon: 'link', elements: [] },
      'datatypeProperty': { label: 'Datatype Properties', icon: 'data_object', elements: [] },
      'annotationProperty': { label: 'Annotation Properties', icon: 'comment', elements: [] },
      'individual': { label: 'Individuals', icon: 'person', elements: [] }
    };

    this.filteredElements.forEach(element => {
      if (typeGroups[element.type]) {
        typeGroups[element.type].elements.push(element);
      }
    });

    this.groupedElements = Object.values(typeGroups)
      .filter(group => group.elements.length > 0)
      .map(group => ({
        ...group,
        count: group.elements.length
      }));
  }

  /**
   * Apply type and search filters
   */
  applyFilters(): void {
    this.filteredElements = this.elements.filter(element => {
      // Type filter
      if (this.selectedType !== 'all' && element.type !== this.selectedType) {
        return false;
      }

      // Search filter
      if (this.searchTerm) {
        const term = this.searchTerm.toLowerCase();
        return element.label.toLowerCase().includes(term) ||
               element.uri.toLowerCase().includes(term) ||
               (element.description?.toLowerCase().includes(term) || false);
      }

      return true;
    });

    this.groupElements();
  }

  /**
   * Refresh ontology cache from server
   */
  refreshCache(): void {
    this.isLoading = true;
    this.ontologyService.refreshOntologyCache(this.routeId).subscribe({
      next: () => {
        this.snackBar.open('Cache refreshed successfully', 'OK', { duration: 3000 });
        this.loadOntologyElements();
      },
      error: (error) => {
        this.error = 'Failed to refresh cache';
        this.isLoading = false;
        this.snackBar.open('Failed to refresh cache', 'OK', { duration: 3000 });
        console.error('Refresh cache error:', error);
      }
    });
  }

  /**
   * Copy URI to clipboard
   */
  copyUri(uri: string, event: Event): void {
    event.stopPropagation();
    navigator.clipboard.writeText(uri).then(() => {
      this.snackBar.open('URI copied to clipboard', '', { duration: 2000 });
    }).catch(err => {
      console.error('Failed to copy URI:', err);
      this.snackBar.open('Failed to copy URI', 'OK', { duration: 2000 });
    });
  }

  /**
   * Close the dialog
   */
  close(): void {
    this.dialogRef.close();
  }
}
