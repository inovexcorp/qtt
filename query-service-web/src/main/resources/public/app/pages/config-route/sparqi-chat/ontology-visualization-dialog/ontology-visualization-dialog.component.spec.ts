import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { OntologyVisualizationDialogComponent } from './ontology-visualization-dialog.component';
import { OntologyService } from '../../../../core/services/ontology/ontology.service';
import { OntologyElement } from '../../../../core/models/ontology/ontology-element';
import { NO_ERRORS_SCHEMA } from '@angular/core';

describe('OntologyVisualizationDialogComponent', () => {
  let component: OntologyVisualizationDialogComponent;
  let fixture: ComponentFixture<OntologyVisualizationDialogComponent>;
  let mockDialogRef: jasmine.SpyObj<MatDialogRef<OntologyVisualizationDialogComponent>>;
  let mockOntologyService: jasmine.SpyObj<OntologyService>;
  let mockSnackBar: jasmine.SpyObj<MatSnackBar>;

  const mockData = {
    routeId: 'test-route',
    elementCount: 100
  };

  beforeEach(async () => {
    mockDialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    mockOntologyService = jasmine.createSpyObj('OntologyService', [
      'getOntologyElements',
      'refreshOntologyCache'
    ]);
    mockSnackBar = jasmine.createSpyObj('MatSnackBar', ['open']);

    await TestBed.configureTestingModule({
      declarations: [OntologyVisualizationDialogComponent],
      imports: [HttpClientTestingModule],
      providers: [
        { provide: MatDialogRef, useValue: mockDialogRef },
        { provide: MAT_DIALOG_DATA, useValue: mockData },
        { provide: OntologyService, useValue: mockOntologyService },
        { provide: MatSnackBar, useValue: mockSnackBar }
      ],
      schemas: [NO_ERRORS_SCHEMA]
    }).compileComponents();

    fixture = TestBed.createComponent(OntologyVisualizationDialogComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize with data from dialog', () => {
    expect(component.routeId).toBe('test-route');
    expect(component.elementCount).toBe(100);
  });

  describe('loadOntologyElements', () => {
    it('should load ontology elements successfully', () => {
      const mockElements: OntologyElement[] = [
        { uri: 'http://example.org/Class1', label: 'Class 1', type: 'class' },
        { uri: 'http://example.org/Property1', label: 'Property 1', type: 'objectProperty' }
      ];

      mockOntologyService.getOntologyElements.and.returnValue(of(mockElements));

      component.ngOnInit();

      expect(mockOntologyService.getOntologyElements).toHaveBeenCalledWith({
        routeId: 'test-route',
        type: 'all',
        limit: 1000
      });
      expect(component.elements.length).toBe(2);
      expect(component.isLoading).toBe(false);
      expect(component.error).toBeNull();
    });

    it('should deduplicate elements', () => {
      const mockElements: OntologyElement[] = [
        { uri: 'http://example.org/Class1', label: 'Class 1', type: 'class' },
        { uri: 'http://example.org/Class1', label: 'Class 1', type: 'class' },
        { uri: 'http://example.org/Property1', label: 'Property 1', type: 'objectProperty' }
      ];

      mockOntologyService.getOntologyElements.and.returnValue(of(mockElements));

      component.ngOnInit();

      expect(component.elements.length).toBe(2);
    });

    it('should calculate statistics correctly', () => {
      const mockElements: OntologyElement[] = [
        { uri: 'http://example.org/Class1', label: 'Class 1', type: 'class' },
        { uri: 'http://example.org/Class2', label: 'Class 2', type: 'class' },
        { uri: 'http://example.org/Property1', label: 'Property 1', type: 'objectProperty' },
        { uri: 'http://example.org/Property2', label: 'Property 2', type: 'datatypeProperty' },
        { uri: 'http://example.org/Individual1', label: 'Individual 1', type: 'individual' }
      ];

      mockOntologyService.getOntologyElements.and.returnValue(of(mockElements));

      component.ngOnInit();

      expect(component.statistics.classes).toBe(2);
      expect(component.statistics.objectProperties).toBe(1);
      expect(component.statistics.datatypeProperties).toBe(1);
      expect(component.statistics.individuals).toBe(1);
      expect(component.statistics.total).toBe(5);
    });

    it('should show warning when limit is reached', () => {
      const mockElements: OntologyElement[] = new Array(1000).fill(null).map((_, i) => ({
        uri: `http://example.org/Class${i}`,
        label: `Class ${i}`,
        type: 'class' as const
      }));

      component.elementCount = 1500;
      mockOntologyService.getOntologyElements.and.returnValue(of(mockElements));

      spyOn(console, 'warn');

      component.ngOnInit();

      expect(console.warn).toHaveBeenCalled();
      expect(mockSnackBar.open).toHaveBeenCalledWith(
        'Showing first 1000 of 1500 elements',
        'OK',
        { duration: 5000 }
      );
    });

    it('should handle error when loading elements', () => {
      mockOntologyService.getOntologyElements.and.returnValue(
        throwError(() => new Error('Failed to load'))
      );

      spyOn(console, 'error');

      component.ngOnInit();

      expect(component.error).toBe('Failed to load ontology elements. Please try again.');
      expect(component.isLoading).toBe(false);
      expect(console.error).toHaveBeenCalled();
    });
  });

  describe('applyFilters', () => {
    beforeEach(() => {
      component.elements = [
        { uri: 'http://example.org/Person', label: 'Person', type: 'class', description: 'A person entity' },
        { uri: 'http://example.org/hasName', label: 'hasName', type: 'objectProperty' },
        { uri: 'http://example.org/age', label: 'age', type: 'datatypeProperty' }
      ];
    });

    it('should filter by type', () => {
      component.selectedType = 'class';
      component.applyFilters();

      expect(component.filteredElements.length).toBe(1);
      expect(component.filteredElements[0].type).toBe('class');
    });

    it('should filter by search term in label', () => {
      component.searchTerm = 'Person';
      component.applyFilters();

      expect(component.filteredElements.length).toBe(1);
      expect(component.filteredElements[0].label).toBe('Person');
    });

    it('should filter by search term in URI', () => {
      component.searchTerm = 'hasName';
      component.applyFilters();

      expect(component.filteredElements.length).toBe(1);
      expect(component.filteredElements[0].uri).toContain('hasName');
    });

    it('should filter by search term in description', () => {
      component.searchTerm = 'person entity';
      component.applyFilters();

      expect(component.filteredElements.length).toBe(1);
      expect(component.filteredElements[0].description).toContain('person entity');
    });

    it('should apply both type and search filters', () => {
      component.elements.push(
        { uri: 'http://example.org/Animal', label: 'Animal', type: 'class' }
      );
      component.selectedType = 'class';
      component.searchTerm = 'Person';
      component.applyFilters();

      expect(component.filteredElements.length).toBe(1);
      expect(component.filteredElements[0].label).toBe('Person');
    });

    it('should show all elements when no filters applied', () => {
      component.selectedType = 'all';
      component.searchTerm = '';
      component.applyFilters();

      expect(component.filteredElements.length).toBe(3);
    });
  });

  describe('refreshCache', () => {
    it('should refresh cache and reload elements', () => {
      const mockElements: OntologyElement[] = [
        { uri: 'http://example.org/Class1', label: 'Class 1', type: 'class' }
      ];

      mockOntologyService.refreshOntologyCache.and.returnValue(of({ success: true }));
      mockOntologyService.getOntologyElements.and.returnValue(of(mockElements));

      component.refreshCache();

      expect(component.isLoading).toBe(true);
      expect(mockOntologyService.refreshOntologyCache).toHaveBeenCalledWith('test-route');
      expect(mockSnackBar.open).toHaveBeenCalledWith(
        'Cache refreshed successfully',
        'OK',
        { duration: 3000 }
      );
    });

    it('should handle refresh cache error', () => {
      mockOntologyService.refreshOntologyCache.and.returnValue(
        throwError(() => new Error('Refresh failed'))
      );

      spyOn(console, 'error');

      component.refreshCache();

      expect(component.error).toBe('Failed to refresh cache');
      expect(component.isLoading).toBe(false);
      expect(mockSnackBar.open).toHaveBeenCalledWith(
        'Failed to refresh cache',
        'OK',
        { duration: 3000 }
      );
      expect(console.error).toHaveBeenCalled();
    });
  });

  describe('copyUri', () => {
    it('should copy URI to clipboard', async () => {
      const uri = 'http://example.org/Class1';
      const mockEvent = new Event('click');
      spyOn(mockEvent, 'stopPropagation');

      Object.assign(navigator, {
        clipboard: {
          writeText: jasmine.createSpy('writeText').and.returnValue(Promise.resolve())
        }
      });

      await component.copyUri(uri, mockEvent);

      expect(mockEvent.stopPropagation).toHaveBeenCalled();
      expect(navigator.clipboard.writeText).toHaveBeenCalledWith(uri);
      expect(mockSnackBar.open).toHaveBeenCalledWith(
        'URI copied to clipboard',
        '',
        { duration: 2000 }
      );
    });

    it('should handle clipboard copy error', async () => {
      const uri = 'http://example.org/Class1';
      const mockEvent = new Event('click');

      Object.assign(navigator, {
        clipboard: {
          writeText: jasmine.createSpy('writeText').and.returnValue(
            Promise.reject(new Error('Copy failed'))
          )
        }
      });

      spyOn(console, 'error');

      await component.copyUri(uri, mockEvent);

      expect(console.error).toHaveBeenCalled();
      expect(mockSnackBar.open).toHaveBeenCalledWith(
        'Failed to copy URI',
        'OK',
        { duration: 2000 }
      );
    });
  });

  describe('close', () => {
    it('should close the dialog', () => {
      component.close();
      expect(mockDialogRef.close).toHaveBeenCalled();
    });
  });

  describe('groupElements', () => {
    it('should group elements by type', () => {
      component.filteredElements = [
        { uri: 'http://example.org/Class1', label: 'Class 1', type: 'class' },
        { uri: 'http://example.org/Class2', label: 'Class 2', type: 'class' },
        { uri: 'http://example.org/Property1', label: 'Property 1', type: 'objectProperty' }
      ];

      component['groupElements']();

      expect(component.groupedElements.length).toBeGreaterThan(0);
      const classGroup = component.groupedElements.find(g => g.label === 'Classes');
      expect(classGroup).toBeDefined();
      expect(classGroup?.count).toBe(2);
    });

    it('should exclude empty groups', () => {
      component.filteredElements = [
        { uri: 'http://example.org/Class1', label: 'Class 1', type: 'class' }
      ];

      component['groupElements']();

      const individualGroup = component.groupedElements.find(g => g.label === 'Individuals');
      expect(individualGroup).toBeUndefined();
    });
  });
});
