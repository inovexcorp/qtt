import { Component, ElementRef, OnInit, AfterViewInit, OnDestroy, ViewChild, Inject } from '@angular/core';
import { FormGroup, FormControl, Validators } from '@angular/forms';
import { ViewEncapsulation } from '@angular/core';
import { trigger, state, style, transition, animate } from '@angular/animations';
import { Datasources } from '../../core/models/datasources';
import { DatasourceService } from '../../core/services/datasource.service';
import { ConfigRouteService } from './config-route.service';
import { NewRoute } from '../../core/models/routes-interfaces/new-route';
import { Router, ActivatedRoute } from '@angular/router';
import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';
import { map, Observable, startWith } from 'rxjs';
import { Graphmarts } from '../../core/models/graphmarts';
import { GraphmartsService } from '../../core/services/graphmarts.service';
import { GraphmartLayers } from '../../core/models/graphmart-layers';
import { COMMA, ENTER } from '@angular/cdk/keycodes';
import { LayersService } from '../../core/services/layers.service';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatChipInputEvent } from '@angular/material/chips';
import { OntologyService } from '../../core/services/ontology/ontology.service';
import { registerOntologyAutocomplete, OntologyAutocompleteProvider } from '../../core/services/ontology/ontology-autocomplete.provider';
import { RoutesService } from '../routes/routes.service';
import { Routes } from '../../core/models/routes-interfaces/routes';
import { DrawerService } from '../../core/services/drawer.service';
import { SparqiService } from '../../core/services/sparqi.service';
import { CacheService } from '../../core/services/cache.service';
import { CacheInfo, RouteCacheStats } from '../../core/models/cache-info';
import { MatDialog, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import * as monaco from 'monaco-editor';

// Import ALL editor features including suggest
import 'monaco-editor/esm/vs/editor/edcore.main.js';
import 'monaco-editor/esm/vs/editor/editor.all.js';



@Component({
  selector: 'app-config-route',
  templateUrl: './config-route.component.html',
  styleUrls: ['./config-route.component.scss'],
  encapsulation: ViewEncapsulation.None,
  animations: [
    trigger('slideDown', [
      transition(':enter', [
        style({ height: 0, opacity: 0, overflow: 'hidden' }),
        animate('300ms ease-out', style({ height: '*', opacity: 1 }))
      ]),
      transition(':leave', [
        animate('300ms ease-in', style({ height: 0, opacity: 0, overflow: 'hidden' }))
      ])
    ])
  ]
})
export class ConfigRouteComponent implements OnInit, AfterViewInit, OnDestroy {

  selectedDatasource: string = '';
  datasourceIcon: string = 'assets/icons/anzo.svg';
  fileName: string = '';
  fileReader = new FileReader()
  templateContent: string | ArrayBuffer | null = '';
  datasources: Datasources[] = [];
  routeId: string = '';
  routeData: Routes | null = null;

  // Datasource health status tracking
  datasourceStatus?: string;
  datasourceHealthError?: string;
  datasourceConsecutiveFailures?: number;
  showTemplateEditor: boolean = false;
  isFullscreen: boolean = false;
  isChatPanelOpen: boolean = false;
  isSparqiEnabled: boolean = false;
  chatPanelWidth: number = 400; // Default width in pixels
  private minChatWidth: number = 300;
  private maxChatWidth: number = 800;
  private isResizing: boolean = false;
  editorOptions = {
    theme: 'vs-dark',
    language: 'freemarker2',
    automaticLayout: true,
    minimap: { enabled: false },
    // CRITICAL: Enable suggestions
    suggestOnTriggerCharacters: true,
    quickSuggestionsDelay: 0,
    suggest: {
      showWords: true,
      showSnippets: true,
      snippetsPreventQuickSuggestions: false,
      showMethods: true,
      showFunctions: true,
      showConstructors: true,
      showFields: true,
      showVariables: true,
      showClasses: true,
      showStructs: true,
      showInterfaces: true,
      showModules: true,
      showProperties: true,
      showEvents: true,
      showOperators: true,
      showUnits: true,
      showValues: true,
      showConstants: true,
      showEnums: true,
      showEnumMembers: true,
      showKeywords: true,
      showReferences: true,
      showFolders: true,
      showTypeParameters: true,
      showIssues: true,
      showUsers: true,
      showColors: true
    },
    quickSuggestions: {
      other: 'on',
      comments: false,
      strings: 'on'
    },
    acceptSuggestionOnCommitCharacter: true,
    acceptSuggestionOnEnter: 'on',
    tabCompletion: 'on'
  };
  filteredOptions!: Observable<Graphmarts[]> | undefined;
  graphMarts: Graphmarts[] = [];
  // Layers to display in dropdown
  filteredLayers!: Observable<GraphmartLayers[]> | undefined;
  // Collection of layer titles to utilize in sending to server for persistence
  layers: string[] = [];
  // All of the layers pulled from server
  allLayers: GraphmartLayers[] = [];
  separatorKeysCodes: number[] = [ENTER, COMMA];
  layerPlaceholder: string = this.layers.length < 1 ? "Keep blank to target all layers" : "Add a new layer(s)...";
  private ontologyAutocompleteProvider?: OntologyAutocompleteProvider;
  private monacoEditor?: monaco.editor.IStandaloneCodeEditor;

  // Cache configuration
  cacheInfo?: CacheInfo;
  routeCacheStats?: RouteCacheStats;
  cacheStatsLoading: boolean = false;
  cacheEnabled: boolean = false;
  cacheExpanded: boolean = false;
  clearingCache: boolean = false;
  cacheJustCleared: boolean = false;

  @ViewChild('layerElement') layerElement!: ElementRef<HTMLInputElement>;
  @ViewChild('monacoEditorContainer') monacoEditorContainer!: ElementRef<HTMLDivElement>;

  configRoute = new FormGroup({
    routeParams: new FormControl<string[]>([], Validators.required),
    routeDescription: new FormControl('', Validators.required),
    graphMartUri: new FormControl('', Validators.required),
    template: new FormControl('', Validators.required),
    datasourceValidator: new FormControl('', Validators.required),
    layersInput: new FormControl(''),
    // Cache fields
    cacheEnabled: new FormControl(false),
    cacheTtlSeconds: new FormControl<number | null>(null),
    cacheKeyStrategy: new FormControl('QUERY_HASH')

  })
  get template() { return this.configRoute.get('template') }
  get routeParameters() { return this.configRoute.get('routeParameters'); }
  get routeDescription() { return this.configRoute.get('routeDescription'); }
  get graphMartUri() { return this.configRoute.get('graphMartUri') }
  get datasourceValidator() { return this.configRoute.get('datasourceValidator') }
  get layersInput() { return this.configRoute.get('layersInput') }
  get cacheEnabledControl() { return this.configRoute.get('cacheEnabled') }
  get cacheTtlSecondsControl() { return this.configRoute.get('cacheTtlSeconds') }
  get cacheKeyStrategyControl() { return this.configRoute.get('cacheKeyStrategy') }



  constructor(
    private domSanitzer: DomSanitizer,
    private route: ActivatedRoute,
    private datasourceService: DatasourceService,
    private configRouteService: ConfigRouteService,
    private routesService: RoutesService,
    public router: Router,
    private matIconRegistry: MatIconRegistry,
    private graphMartService: GraphmartsService,
    private layersService: LayersService,
    private ontologyService: OntologyService,
    private drawerService: DrawerService,
    private sparqiService: SparqiService,
    private cacheService: CacheService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {
    this.matIconRegistry.addSvgIcon(
      'anzo-logo',
      this.domSanitzer.bypassSecurityTrustResourceUrl('assets/icons/anzo.svg')
    );
  }

  navigateBack(): void {
    this.router.navigate(['/routes']);
  }

  onFileSelected(event: Event) {

    if ((!<HTMLInputElement>event.target)) return;

    let file = (<HTMLInputElement>event.target).files;

    if (file) {
      this.fileName = file[0].name;
      this.fileReader.onload = (e) => {
        this.templateContent = this.fileReader.result;
        this.configRoute.controls['template'].setValue(this.templateContent as string);
      }
      this.fileReader.readAsText(file[0]);
    }
  }

  getDatasources(): void {
    this.datasourceService.getDatasources()
      .subscribe(datasources => this.datasources = datasources);
  }

  // Get FTL template content
  getTemplateContent(routeId: string): void {
    this.configRouteService.getTemplate(this.routeId)
      .subscribe(templateContent => {
        this.templateContent = templateContent;
        this.configRoute.controls['template'].setValue(this.templateContent);
      }
      );
  }

  // Modify a Route
  putRoute(): void {
    let httpMethods: string[] = this.configRoute.value['routeParams'] as string[];
    let routeParams: string = 'httpMethodRestrict=' + httpMethods.join(',');
    let routeDescription: string = this.configRoute.value['routeDescription'] as string;
    let graphMartTitle: string = this.configRoute.value['graphMartUri'] as string;
    let templateBody = this.configRoute.value['template'];
    let dataSourceId = this.configRoute.value['datasourceValidator'] as string;
    let graphMartUri = this.graphMarts.find(item => item.title === graphMartTitle)?.iri;
    let routeId = this.routeId
    let layers: string = this.concatenanteIris(this.layers, this.allLayers);

    // Cache parameters
    let cacheEnabled = this.configRoute.value['cacheEnabled'] as boolean;
    let cacheTtlSeconds = this.configRoute.value['cacheTtlSeconds'] as number | null;
    let cacheKeyStrategy = this.configRoute.value['cacheKeyStrategy'] as string;

    if (!httpMethods || httpMethods.length === 0 || !routeDescription || !graphMartUri || !templateBody || !dataSourceId) { return; }

    this.configRouteService.configRoute({ routeId, routeParams, dataSourceId, routeDescription, graphMartUri, templateBody, layers, cacheEnabled, cacheTtlSeconds, cacheKeyStrategy } as NewRoute)
      .subscribe(() => {
        this.navigateBack();
      });
  }

  loadCacheInfo(): void {
    this.cacheService.getCacheInfo().subscribe({
      next: (response: any) => {
        // Extract the nested info object from the response
        this.cacheInfo = response.info;
        // Set default TTL from global config if not already set
        if (this.cacheInfo && this.cacheInfo.defaultTtlSeconds && !this.configRoute.value['cacheTtlSeconds']) {
          this.configRoute.controls['cacheTtlSeconds'].setValue(this.cacheInfo.defaultTtlSeconds);
        }
        // Load route-specific cache stats after cache info is loaded
        this.loadRouteCacheStats();
      },
      error: (error) => {
        console.error('Failed to load cache info:', error);
      }
    });
  }

  loadRouteCacheStats(): void {
    if (!this.cacheInfo || !this.cacheInfo.enabled) {
      return;
    }

    this.cacheStatsLoading = true;
    this.cacheService.getRouteCacheStats(this.routeId).subscribe({
      next: (stats) => {
        this.routeCacheStats = stats;
        this.cacheStatsLoading = false;
      },
      error: (error) => {
        console.error('Failed to load route cache stats:', error);
        this.cacheStatsLoading = false;
      }
    });
  }

  getCacheHitRatio(): string {
    if (!this.routeCacheStats || !this.routeCacheStats.globalStats) {
      return '0.0';
    }
    const hits = this.routeCacheStats.globalStats.hits;
    const misses = this.routeCacheStats.globalStats.misses;
    const total = hits + misses;
    if (total === 0) {
      return '0.0';
    }
    return ((hits / total) * 100).toFixed(1);
  }

  toggleCacheEnabled(): void {
    // Note: ngModel already updated cacheEnabled, so don't toggle it again
    this.configRoute.controls['cacheEnabled'].setValue(this.cacheEnabled);
    if (this.cacheEnabled) {
      this.cacheExpanded = true;
    }
  }

  toggleCacheExpanded(): void {
    this.cacheExpanded = !this.cacheExpanded;
  }

  clearRouteCache(): void {
    // Show confirmation dialog
    const dialogRef = this.dialog.open(CacheClearConfirmDialog, {
      width: '400px',
      data: { routeId: this.routeId }
    });

    dialogRef.afterClosed().subscribe(confirmed => {
      if (confirmed) {
        // Set loading state
        this.clearingCache = true;
        this.cacheJustCleared = false;

        // Call cache service to clear cache
        this.cacheService.clearRouteCache(this.routeId).subscribe({
          next: (response) => {
            // Clear loading state
            this.clearingCache = false;

            // Show success state
            this.cacheJustCleared = true;
            setTimeout(() => {
              this.cacheJustCleared = false;
            }, 2000);

            // Show success toast with deleted count
            const deletedCount = response.deletedCount || 0;
            this.snackBar.open(
              `Cache cleared successfully. Deleted ${deletedCount} ${deletedCount === 1 ? 'entry' : 'entries'}.`,
              'Close',
              {
                duration: 5000,
                horizontalPosition: 'end',
                verticalPosition: 'top',
                panelClass: ['success-snackbar']
              }
            );

            // Refresh cache stats to show updated counts
            this.loadRouteCacheStats();
          },
          error: (error) => {
            // Clear loading state
            this.clearingCache = false;

            // Show error toast
            const errorMessage = error.error?.message || 'Failed to clear cache';
            this.snackBar.open(
              `Error: ${errorMessage}`,
              'Close',
              {
                duration: 7000,
                horizontalPosition: 'end',
                verticalPosition: 'top',
                panelClass: ['error-snackbar']
              }
            );
          }
        });
      }
    });
  }

  // Method to get and filter graphmarts based on selected DS
  getGraphMarts(dataSourceId: any) {
    this.graphMartService.getGraphMarts(dataSourceId).subscribe({
      next: (graphMarts) => {
        this.graphMarts = graphMarts;
        this.filteredOptions = this.configRoute.get('graphMartUri')?.valueChanges.pipe(
          startWith(''),
          map(value => this.filterGraphmarts(value || '')),
        );
      },
      error: (error) => {
        this.graphMarts = [];
        this.filteredOptions = undefined;
        this.configRoute.controls['graphMartUri'].setValue("");
        console.log(error);

      }
    }
    );
  }

  // Method used to load and filter initial graphmart of configured route
  loadGraphMarts(dataSourceId: any) {
    this.graphMartService.getGraphMarts(dataSourceId).subscribe({
      next: (graphMarts) => {
        this.graphMarts = graphMarts;
        this.filteredOptions = this.configRoute.get('graphMartUri')?.valueChanges.pipe(
          startWith(''),
          map(value => this.filterGraphmarts(value || '')),
        );
        if (this.routeData) {
          // Find and set the graphmart title
          const graphmartMatch = this.graphMarts.find(obj => obj.iri === this.routeData!.graphMartUri);
          if (graphmartMatch) {
            this.configRoute.controls['graphMartUri'].setValue(graphmartMatch.title);
          } else {
            // GraphMart not found in list, but preserve the IRI from routeData
            this.configRoute.controls['graphMartUri'].setValue(this.routeData.graphMartUri);
          }
        }
      },
      error: (error) => {
        console.error('Failed to load graphmarts:', error);
        this.graphMarts = [];
        this.filteredOptions = undefined;

        // PRESERVE the saved graphMartUri from routeData instead of clearing
        if (this.routeData && this.routeData.graphMartUri) {
          this.configRoute.controls['graphMartUri'].setValue(this.routeData.graphMartUri);
          // Disable the field to prevent changes when datasource is down
          this.configRoute.controls['graphMartUri'].disable();
        }
      }
    }
    );
  }

  //Re fetch layers when new gm is selected
  getGraphMartLayers(graphMart: any) {
    this.layers = [];
    graphMart = this.graphMarts.find(item => item.title === graphMart)?.iri as string;
    this.layersService.getGraphmartLayers(this.configRoute.value['datasourceValidator'] as string, graphMart).subscribe({
      next: (layer) => {
        this.allLayers = layer;
        this.filteredLayers = this.configRoute.get('layersInput')?.valueChanges.pipe(
          startWith(''),
          map(value => this.filterLayers(value || '')),
        )
      },
      error: (error) => {
        console.log(error);

      }
    }
    )
  }

  // Fetch associated graphmart layers AND all graphmart layers
  loadGraphMartLayers(datasourceId: any, graphMart: any) {
    this.layersService.getGraphmartLayers(datasourceId, graphMart).subscribe({
      next: (layer) => {
        this.allLayers = layer;
        this.loadAssociatedLayers(this.routeId);
        this.filteredLayers = this.configRoute.get('layersInput')?.valueChanges.pipe(
          startWith(''),
          map(value => this.filterLayers(value || '')),
        )
      },
      error: (error) => {
        console.error('Failed to load graphmart layers:', error);
        this.allLayers = [];

        // STILL LOAD associated layers from database to preserve UI state
        this.loadAssociatedLayers(this.routeId);

        // Disable layer input when datasource is unavailable
        this.configRoute.controls['layersInput']?.disable();
      }
    }
    )
  }

  // Load layers tied to a route
  loadAssociatedLayers(routeId: string){
    let associatedLayers: String[]=[];
    this.layersService.getRouteLayers(routeId).subscribe({
      next: (layer) => {
        // Load layers associated to a route
        associatedLayers = layer;
        // Loop through each layer uri associated to the route
        for (let iri of associatedLayers) {
          // See if the iri exists within all the layers pulled for specific graphmart
          let match = this.allLayers.find(obj => obj.iri === iri);
          // If a match is found, add the iri's title to the angular chips
          if (match) {
            this.layers.push(match.title);
          }
        }
        this.layerPlaceholder= this.layers.length < 1 ? "Keep blank to target all layers" : "Add a new layer(s)...";

      }
    })
  }

  add(event: MatChipInputEvent): void {
    const value = (event.value || '').trim();

    // Ensure custom layers cannot be specified, only those listed.
    if (!this.allLayers.some(layer => layer.title == value)) {
      console.log("Cannot specify layer which does not exist.")
      return;
    }

    if (this.layers.includes(value)) {
      console.log("Layer: " + value + " already included.")
    }
    // Add our layer
    else if (value) {
      this.layers.push(value);
    }

    // Clear the input value
    event.chipInput!.clear();
    this.configRoute.get('layersInput')?.setValue(null);

    // Update placeholder
    if (this.layers) {
      this.layerPlaceholder = "Add a new layer(s)...";

    }

  }

  remove(layer: string): void {
    const index = this.layers.indexOf(layer);

    if (index >= 0) {
      this.layers.splice(index, 1);
    }

    if (this.layers.length < 1) {
      this.layerPlaceholder = "Keep blank to target all layers"
    }
  }

  selected(event: MatAutocompleteSelectedEvent): void {
    if (this.layers.includes(event.option.viewValue)) {
      console.log("Layer: " + event.option.viewValue + " already included.");
      this.layerElement.nativeElement.value = '';
      this.configRoute.get('layersInput')?.setValue(null);
    }
    else if (event.option.viewValue) {
      this.layers.push(event.option.viewValue);
      this.layerElement.nativeElement.value = '';
      this.configRoute.get('layersInput')?.setValue(null);
    }

    // Update placeholder
    if (this.layers) {
      this.layerPlaceholder = "Add a new layer(s)...";

    }
  }

  checkGraphMartStatus(graphMartTitle: string): boolean {
    let graphMartMatch = this.graphMarts.find(obj => obj.title === graphMartTitle);
    if (graphMartMatch && graphMartMatch.active === 'true') {
      return true;
    } else {
      return false;
    }
  }

  filterGraphmarts(searchTerm: string): Graphmarts[] {
    const filteredOptions = this.graphMarts.filter(graphmart => {
      return graphmart.title.toLowerCase().includes(searchTerm.toLowerCase());
    });
    return filteredOptions;
  }

  filterLayers(searchTerm: string): GraphmartLayers[] {
    const filteredOptions = this.allLayers.filter(layer => {
      return layer.title.toLowerCase().includes(searchTerm.toLowerCase());
    });
    return filteredOptions;
  }

  concatenanteIris(layers: string[], allLayers: GraphmartLayers[]): string {
    const iris: string[] = [];
    for (const title of layers) {
      const matchingItem = allLayers.find(item => item.title === title);
      if (matchingItem) {
        iris.push(matchingItem.iri);
      }
    }
    return iris.join(",");
  }

  chipStatus(title: string): boolean {
    const foundLayer = this.allLayers.find(layer => layer.title === title);
    return foundLayer?.active === "true";
  }

  onEditorInit(editor: any): void {
    // Configure word pattern FIRST, before registering providers
    // This prevents Monaco from breaking URIs at dots, slashes, etc.
    monaco.languages.setLanguageConfiguration('freemarker2', {
      wordPattern: /(-?\d*\.\d\w*)|([^\`\~\!\@\#\%\^\&\*\(\)\=\+\[\{\]\}\\\|\;\:\'\"\,\?\s]+)/g,
    });

    // Add a small delay to ensure Monaco is fully ready
    setTimeout(() => {
      this.initializeOntologyAutocomplete(this.routeId);
    }, 100);
  }

  private initializeOntologyAutocomplete(routeId: string): void {
    if (this.ontologyAutocompleteProvider) {
      // Update existing provider with new route ID
      this.ontologyAutocompleteProvider.setRouteId(routeId);
    } else {
      // Register new provider
      this.ontologyAutocompleteProvider = registerOntologyAutocomplete(
        this.ontologyService,
        routeId
      );
    }
    // Warm cache for better performance
    this.ontologyService.warmCache(routeId).subscribe();
  }

  ngOnInit(): void {
    // Get routeId from URL params
    this.route.params.subscribe(params => {
      this.routeId = params['routeId'];

      // Load the route data
      this.loadRouteData();
    });

    this.getDatasources();

    // Check if SPARQI is enabled
    this.checkSparqiStatus();

    // Load saved chat panel width from localStorage
    const savedWidth = localStorage.getItem('sparqi-panel-width');
    if (savedWidth) {
      this.chatPanelWidth = parseInt(savedWidth, 10);
    }
  }

  // Load route data from the server
  loadRouteData(): void {
    this.routesService.getRoutes().subscribe(routes => {
      this.routeData = routes.find(r => r.routeId === this.routeId) || null;

      if (this.routeData) {
        this.selectedDatasource = this.routeData.datasources.dataSourceId;
        this.fileName = this.routeId;

        // Load datasource health status
        this.loadDatasourceStatus(this.routeData.datasources.dataSourceId);

        // Load dependent data
        this.getTemplateContent(this.routeId);
        this.loadGraphMarts(this.routeData.datasources.dataSourceId);
        this.loadGraphMartLayers(this.routeData.datasources.dataSourceId, this.routeData.graphMartUri);

        // Parse the existing routeParams to extract HTTP methods
        const existingParams = this.routeData.routeParams;
        let httpMethods: string[] = [];
        if (existingParams && existingParams.includes('httpMethodRestrict=')) {
          const methodsString = existingParams.split('httpMethodRestrict=')[1];
          httpMethods = methodsString.split(',');
        }
        this.configRoute.controls['routeParams'].setValue(httpMethods);

        this.configRoute.controls['routeDescription'].setValue(this.routeData.description);
        this.configRoute.controls['datasourceValidator'].setValue(this.routeData.datasources.dataSourceId);

        // Load cache configuration
        if (this.routeData.cacheEnabled !== undefined) {
          this.cacheEnabled = this.routeData.cacheEnabled;
          this.configRoute.controls['cacheEnabled'].setValue(this.routeData.cacheEnabled);
        }
        if (this.routeData.cacheTtlSeconds !== undefined) {
          this.configRoute.controls['cacheTtlSeconds'].setValue(this.routeData.cacheTtlSeconds);
        }
        if (this.routeData.cacheKeyStrategy) {
          this.configRoute.controls['cacheKeyStrategy'].setValue(this.routeData.cacheKeyStrategy);
        }

        // Load cache info for displaying defaults
        this.loadCacheInfo();
      }
    });
  }

  // Load datasource health status
  loadDatasourceStatus(dataSourceId: string): void {
    this.datasourceService.getDatasource(dataSourceId).subscribe({
      next: (datasource) => {
        this.datasourceStatus = datasource.status;
        this.datasourceHealthError = datasource.lastHealthError;
        this.datasourceConsecutiveFailures = datasource.consecutiveFailures;
      },
      error: (error) => {
        console.error('Failed to load datasource status:', error);
      }
    });
  }

  // Helper methods for status display
  getStatusIcon(status?: string): string {
    switch(status) {
      case 'UP': return 'check_circle';
      case 'DOWN': return 'error';
      case 'DISABLED': return 'block';
      case 'CHECKING': return 'sync';
      default: return 'help';
    }
  }

  getStatusClass(status?: string): string {
    switch(status) {
      case 'UP': return 'status-up';
      case 'DOWN': return 'status-down';
      case 'DISABLED': return 'status-disabled';
      case 'CHECKING': return 'status-checking';
      default: return 'status-unknown';
    }
  }

  isDatasourceHealthy(): boolean {
    return this.datasourceStatus === 'UP' || this.datasourceStatus === 'CHECKING';
  }

  // Check if SPARQI is enabled
  checkSparqiStatus(): void {
    this.sparqiService.checkHealth().subscribe({
      next: (response) => {
        // SPARQI is enabled if status is "available"
        this.isSparqiEnabled = response.status === 'available';
      },
      error: (error) => {
        // If health check fails, assume SPARQI is disabled
        console.log('SPARQI not available:', error);
        this.isSparqiEnabled = false;
      }
    });
  }

  ngAfterViewInit(): void {
    // Create the Monaco editor on view init
    setTimeout(() => {
      this.createMonacoEditor();
    }, 100);
  }

  toggleEditor(): void {
    this.showTemplateEditor = !this.showTemplateEditor;

    if (this.showTemplateEditor && !this.monacoEditor) {
      // Wait for Angular to render the div
      setTimeout(() => {
        this.createMonacoEditor();
      }, 50);
    } else if (this.showTemplateEditor && this.monacoEditor) {
      // Editor already exists, just trigger layout after animation
      setTimeout(() => {
        this.monacoEditor?.layout();
      }, 350); // Slightly longer than the 300ms transition
    }
  }

  toggleFullscreen(): void {
    this.isFullscreen = !this.isFullscreen;

    // Close the navigation drawer when entering fullscreen
    if (this.isFullscreen) {
      this.drawerService.requestCloseDrawer();
    }

    // Trigger editor layout after fullscreen animation
    setTimeout(() => {
      this.monacoEditor?.layout();
    }, 350);
  }

  toggleChatPanel(): void {
    this.isChatPanelOpen = !this.isChatPanelOpen;
    // Trigger editor layout after chat panel animation
    setTimeout(() => {
      this.monacoEditor?.layout();
    }, 350);
  }

  /**
   * Start resizing the chat panel
   */
  startResize(event: MouseEvent): void {
    event.preventDefault();
    this.isResizing = true;

    const startX = event.clientX;
    const startWidth = this.chatPanelWidth;

    const onMouseMove = (e: MouseEvent) => {
      if (!this.isResizing) return;

      // Calculate new width (dragging left increases width)
      const deltaX = startX - e.clientX;
      let newWidth = startWidth + deltaX;

      // Enforce min/max constraints
      newWidth = Math.max(this.minChatWidth, Math.min(this.maxChatWidth, newWidth));

      this.chatPanelWidth = newWidth;

      // Trigger editor layout during resize
      this.monacoEditor?.layout();
    };

    const onMouseUp = () => {
      this.isResizing = false;

      // Save width to localStorage
      localStorage.setItem('sparqi-panel-width', this.chatPanelWidth.toString());

      // Clean up event listeners
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);

      // Final editor layout
      setTimeout(() => {
        this.monacoEditor?.layout();
      }, 50);
    };

    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);
  }

  private createMonacoEditor(): void {
    if (!this.monacoEditorContainer) {
      console.error('Monaco editor container not found');
      return;
    }

    this.monacoEditor = monaco.editor.create(this.monacoEditorContainer.nativeElement, {
      value: this.templateContent as string || '',
      language: 'freemarker2',
      theme: 'vs-dark',
      automaticLayout: true,
      minimap: { enabled: false },
      suggestOnTriggerCharacters: true,
      quickSuggestionsDelay: 0,
      suggest: {
        showWords: true,
        showSnippets: true,
        snippetsPreventQuickSuggestions: false,
        showMethods: true,
        showFunctions: true,
        showConstructors: true,
        showFields: true,
        showVariables: true,
        showClasses: true,
      },
      quickSuggestions: {
        other: 'on' as any,
        comments: false,
        strings: 'on' as any
      }
    });

    // Bind to form control
    this.monacoEditor.onDidChangeModelContent(() => {
      this.configRoute.controls['template'].setValue(this.monacoEditor!.getValue());
    });

    // Wait a bit then initialize autocomplete
    setTimeout(() => {
      this.onEditorInit(this.monacoEditor!);
    }, 100);
  }

  ngOnDestroy(): void {
    if (this.monacoEditor) {
      this.monacoEditor.dispose();
    }
  }

}

// Confirmation Dialog Component for Cache Clearing
@Component({
  selector: 'cache-clear-confirm-dialog',
  template: `
    <h2 mat-dialog-title>Clear Cache for Route?</h2>
    <mat-dialog-content>
      <p>Are you sure you want to clear all cached query results for route <strong>{{data.routeId}}</strong>?</p>
      <p class="warning-text">
        <mat-icon class="warning-icon">warning</mat-icon>
        This will remove all cached data for this route. Future queries will need to be re-executed and cached again.
      </p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">Cancel</button>
      <button mat-raised-button color="warn" [mat-dialog-close]="true" cdkFocusInitial>Clear Cache</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .warning-text {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-top: 16px;
      padding: 12px;
      background-color: #fff3cd;
      border-radius: 4px;
      color: #856404;
    }
    .warning-icon {
      color: #ff9800;
    }
    mat-dialog-content p {
      margin: 8px 0;
    }
  `]
})
export class CacheClearConfirmDialog {
  constructor(
    @Inject(MAT_DIALOG_DATA) public data: { routeId: string }
  ) {}
}
