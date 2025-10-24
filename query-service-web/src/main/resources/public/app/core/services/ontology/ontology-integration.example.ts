/**
 * EXAMPLE INTEGRATION: How to integrate ontology autocomplete into route editor components.
 *
 * This file demonstrates how to add ontology autocomplete to the add-route and config-route components.
 * Follow these steps to integrate:
 *
 * 1. Import the necessary services and provider:
 *    ```typescript
 *    import { OntologyService } from '../../core/services/ontology/ontology.service';
 *    import { registerOntologyAutocomplete, OntologyAutocompleteProvider } from '../../core/services/ontology/ontology-autocomplete.provider';
 *    ```
 *
 * 2. Inject OntologyService in the constructor:
 *    ```typescript
 *    constructor(
 *      // ... existing services
 *      private ontologyService: OntologyService
 *    ) {
 *      // ... existing code
 *    }
 *    ```
 *
 * 3. Add a property to hold the autocomplete provider:
 *    ```typescript
 *    private ontologyAutocompleteProvider?: OntologyAutocompleteProvider;
 *    ```
 *
 * 4. Initialize the autocomplete provider when the template editor is shown and a route is created/loaded:
 *    ```typescript
 *    // After route is created or loaded, and you have a routeId
 *    private initializeOntologyAutocomplete(routeId: string): void {
 *      if (this.ontologyAutocompleteProvider) {
 *        // Update existing provider with new route ID
 *        this.ontologyAutocompleteProvider.setRouteId(routeId);
 *      } else {
 *        // Register new provider
 *        this.ontologyAutocompleteProvider = registerOntologyAutocomplete(
 *          this.ontologyService,
 *          routeId
 *        );
 *      }
 *
 *      // Optionally warm the cache for better performance
 *      this.ontologyService.warmCache(routeId).subscribe();
 *    }
 *    ```
 *
 * 5. Call initialization at the appropriate time:
 *
 *    For ADD-ROUTE component:
 *    ```typescript
 *    addRoute(): void {
 *      // ... existing route creation code
 *
 *      this.routeService.postRoute({ routeId, routeParams, dataSourceId, routeDescription, graphMartUri, templateBody, layers } as NewRoute)
 *        .subscribe((response) => {
 *          // Initialize autocomplete after route is created
 *          this.initializeOntologyAutocomplete(routeId);
 *
 *          this.router.navigate(['../../routes']);
 *          location.reload();
 *        });
 *    }
 *    ```
 *
 *    For CONFIG-ROUTE component (when editing existing route):
 *    ```typescript
 *    ngOnInit(): void {
 *      // ... existing initialization code
 *
 *      // Get route ID from route params or service
 *      const routeId = // ... get from ActivatedRoute or other source
 *      if (routeId) {
 *        this.initializeOntologyAutocomplete(routeId);
 *      }
 *    }
 *    ```
 *
 * 6. OPTIONAL: Add UI indicators for cache status
 *    ```typescript
 *    ontologyCacheStatus: string = 'loading';
 *
 *    checkOntologyMetadata(routeId: string): void {
 *      this.ontologyService.getOntologyMetadata(routeId).subscribe({
 *        next: (metadata) => {
 *          this.ontologyCacheStatus = metadata.cached ? 'ready' : 'loading';
 *          console.log(`Ontology cache: ${metadata.elementCount} elements`);
 *        },
 *        error: (err) => {
 *          this.ontologyCacheStatus = 'error';
 *          console.warn('Ontology cache not available:', err);
 *        }
 *      });
 *    }
 *    ```
 *
 * 7. OPTIONAL: Add HTML indicator in the template
 *    ```html
 *    <div *ngIf="showTemplateEditor" class="ontology-status">
 *      <mat-icon *ngIf="ontologyCacheStatus === 'ready'" class="status-icon ready">check_circle</mat-icon>
 *      <mat-icon *ngIf="ontologyCacheStatus === 'loading'" class="status-icon loading">sync</mat-icon>
 *      <mat-icon *ngIf="ontologyCacheStatus === 'error'" class="status-icon error">error</mat-icon>
 *      <span>Ontology Autocomplete: {{ontologyCacheStatus}}</span>
 *    </div>
 *    ```
 *
 * USAGE:
 * Once integrated, users can type in the Monaco editor and will see autocomplete suggestions
 * for ontology URIs. The suggestions will appear:
 * - When typing inside < > brackets (e.g., <http://...)
 * - When typing inside quotes (e.g., "http://...)
 * - When typing at least 2 characters of a label or URI
 *
 * The autocomplete shows:
 * - Label of the ontology element
 * - Full URI as detail
 * - Type indicator (Class, Property, Individual)
 * - Optional description from rdfs:comment
 */
