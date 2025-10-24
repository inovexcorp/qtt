import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { ViewEncapsulation } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { trigger, state, style, transition, animate } from '@angular/animations';
import { DatasourceService } from '../../core/services/datasource.service';
import { Datasources } from '../../core/models/datasources';
import { AddRouteService } from './add-route.service';
import { Router } from '@angular/router';
import { FormGroup, FormControl, Validators } from '@angular/forms';
import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';
import { GraphmartsService } from '../../core/services/graphmarts.service';
import { Graphmarts } from '../../core/models/graphmarts';
import { map, Observable, startWith } from 'rxjs';
import { GraphmartLayers } from '../../core/models/graphmart-layers';
import { COMMA, ENTER } from '@angular/cdk/keycodes';
import { LayersService } from '../../core/services/layers.service';
import { MatChipInputEvent } from '@angular/material/chips';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { NewRoute } from '../../core/models/routes-interfaces/new-route';
import { OntologyService } from '../../core/services/ontology/ontology.service';
import { registerOntologyAutocomplete, OntologyAutocompleteProvider } from '../../core/services/ontology/ontology-autocomplete.provider';
import * as monaco from 'monaco-editor';
import { CacheService } from '../../core/services/cache.service';
import { CacheInfo } from '../../core/models/cache-info';



@Component({
  selector: 'app-add-route',
  templateUrl: './add-route.component.html',
  styleUrls: ['./add-route.component.scss'],
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
export class AddRouteComponent implements OnInit {

  filteredGraphmarts!: Observable<Graphmarts[]> | undefined;
  datasourceIcon: string = 'assets/icons/anzo.svg';
  fileName: string = '';
  fileReader = new FileReader()
  templateContent: string | ArrayBuffer | null = '';
  datasources: Datasources[] = [];
  graphMarts: Graphmarts[] = [];
  // Layers to display in dropdown
  filteredLayers!: Observable<GraphmartLayers[]> | undefined;
  // Collection of layer titles to utilize in sending to server for persistence
  layers: string[] = [];
  // All of the layers pulled from server
  allLayers: GraphmartLayers[] = [];
  separatorKeysCodes: number[] = [ENTER, COMMA];
  showTemplateEditor: boolean = false;
  editorOptions = {
    theme: 'vs-dark',
    language: 'freemarker2',
    automaticLayout: true,
    minimap: { enabled: false },
    suggest: {
      showWords: true,
      showSnippets: true,
      snippetsPreventQuickSuggestions: false
    },
    quickSuggestions: {
      other: true,
      comments: false,
      strings: true
    },
    acceptSuggestionOnCommitCharacter: true,
    acceptSuggestionOnEnter: 'on',
    tabCompletion: 'on'
  };

  layerPlaceholder: string = "Keep blank to target all layers";
  private ontologyAutocompleteProvider?: OntologyAutocompleteProvider;
  private monacoEditor: any;

  // Cache configuration
  cacheInfo?: CacheInfo;
  cacheEnabled: boolean = false;
  cacheExpanded: boolean = false;

  @ViewChild('layerElement') layerElement!: ElementRef<HTMLInputElement>;

  createRoute = new FormGroup({
    routeId: new FormControl('', Validators.required),
    routeParams: new FormControl<string[]>([], Validators.required),
    routeDescription: new FormControl('', Validators.required),
    graphMartUri: new FormControl('', Validators.required),
    template: new FormControl(''), // Allow empty template
    datasourceValidator: new FormControl('', Validators.required),
    layersInput: new FormControl(''),
    // Cache fields
    cacheEnabled: new FormControl(false),
    cacheTtlSeconds: new FormControl<number | null>(null),
    cacheKeyStrategy: new FormControl('QUERY_HASH')
  })
  get routeId() { return this.createRoute.get('routeId') }
  get routeParameters() { return this.createRoute.get('routeParameters'); }
  get routeDescription() { return this.createRoute.get('routeDescription'); }
  get graphMartUri() { return this.createRoute.get('graphMartUri') }
  get template() { return this.createRoute.get('template') }
  get datasourceValidator() { return this.createRoute.get('datasourceValidator') }
  get layersInput() { return this.createRoute.get('layersInput') }
  get cacheEnabledControl() { return this.createRoute.get('cacheEnabled') }
  get cacheTtlSecondsControl() { return this.createRoute.get('cacheTtlSeconds') }
  get cacheKeyStrategyControl() { return this.createRoute.get('cacheKeyStrategy') }




  constructor(private domSanitzer: DomSanitizer, public router: Router, public dialog: MatDialog, private datasourceService: DatasourceService, private routeService: AddRouteService, private graphMartService: GraphmartsService, private matIconRegistry: MatIconRegistry, private layersService: LayersService, private ontologyService: OntologyService, private cacheService: CacheService
  ) {
    this.matIconRegistry.addSvgIcon(
      'anzo-logo',
      this.domSanitzer.bypassSecurityTrustResourceUrl('assets/icons/anzo.svg')
    );
  }

  closeDialog(): void {
    this.dialog.closeAll();
  }

  ngOnInit(): void {
    this.getDatasources();
    this.loadCacheInfo();
  }

  loadCacheInfo(): void {
    this.cacheService.getCacheInfo().subscribe({
      next: (response: any) => {
        // Extract the nested info object from the response
        this.cacheInfo = response.info;
        // Set default TTL from global config
        if (this.cacheInfo && this.cacheInfo.defaultTtlSeconds) {
          this.createRoute.controls['cacheTtlSeconds'].setValue(this.cacheInfo.defaultTtlSeconds);
        }
      },
      error: (error) => {
        console.error('Failed to load cache info:', error);
      }
    });
  }

  toggleCacheEnabled(): void {
    // Note: ngModel already updated cacheEnabled, so don't toggle it again
    this.createRoute.controls['cacheEnabled'].setValue(this.cacheEnabled);
    if (this.cacheEnabled) {
      this.cacheExpanded = true;
    }
  }

  toggleCacheExpanded(): void {
    this.cacheExpanded = !this.cacheExpanded;
  }

  // Method to get and filter graphmarts based on selected DS
  getGraphMarts(dataSourceId: any) {
    this.graphMartService.getGraphMarts(dataSourceId).subscribe({
      next: (graphMarts) => {
        this.graphMarts = graphMarts;
        this.filteredGraphmarts = this.createRoute.get('graphMartUri')?.valueChanges.pipe(
          startWith(''),
          map(value => this.filterGraphmarts(value || '')),
        )
      },
      error: (error) => {
        this.graphMarts = [];
        this.filteredGraphmarts = undefined;
        this.layers = [];
        this.filteredLayers = undefined;
        this.createRoute.controls['layersInput'].setValue("");
        this.createRoute.controls['graphMartUri'].setValue("");
        console.log(error);

      }
    }
    );
  }

  getGraphMartLayers(graphMart: any) {
    this.layers = [];
    graphMart = this.graphMarts.find(item => item.title === graphMart)?.iri as string;
    this.layersService.getGraphmartLayers(this.createRoute.value['datasourceValidator'] as string, graphMart).subscribe({
      next: (layer) => {
        this.allLayers = layer;
        this.filteredLayers = this.createRoute.get('layersInput')?.valueChanges.pipe(
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
    this.createRoute.get('layersInput')?.setValue(null);

    // Update placeholder
    if (this.layers) {
      this.layerPlaceholder = "Add another layer...";

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
      this.createRoute.get('layersInput')?.setValue(null);
    }
    else if (event.option.viewValue) {
      this.layers.push(event.option.viewValue);
      this.layerElement.nativeElement.value = '';
      this.createRoute.get('layersInput')?.setValue(null);
    }

    // Update placeholder
    if (this.layers) {
      this.layerPlaceholder = "Add another layer...";

    }
  }

  onFileSelected(event: Event) {

    if ((!<HTMLInputElement>event.target)) return;

    let file = (<HTMLInputElement>event.target).files;

    if (file) {
      this.fileName = file[0].name;
      this.fileReader.onload = (e) => {
        this.templateContent = this.fileReader.result;
        this.createRoute.controls['template'].setValue(this.templateContent as string);
      }
      this.fileReader.readAsText(file[0]);
    }
  }

  getDatasources(): void {
    this.datasourceService.getDatasources()
      .subscribe(datasources => this.datasources = datasources);
  }

  checkGraphMartStatus(graphMartTitle: string): boolean {
    let graphMartMatch = this.graphMarts.find(obj => obj.title === graphMartTitle);
    if (graphMartMatch && graphMartMatch.active === 'true') {
      return true;
    } else {
      return false;
    }
  }

  onEditorInit(editor: any): void {
    console.log('[Add Route] Monaco Editor initialized');
    console.log('[Add Route] Editor instance:', editor);
    console.log('[Add Route] Editor model language:', editor?.getModel()?.getLanguageId());

    // Store the editor instance
    this.monacoEditor = editor;

    // Configure word pattern FIRST, before registering providers
    // This prevents Monaco from breaking URIs at dots, slashes, etc.
    monaco.languages.setLanguageConfiguration('freemarker2', {
      wordPattern: /(-?\d*\.\d\w*)|([^\`\~\!\@\#\%\^\&\*\(\)\=\+\[\{\]\}\\\|\;\:\'\"\,\?\s]+)/g,
    });

    // Add a small delay to ensure Monaco is fully ready
    setTimeout(() => {
      console.log('[Add Route] Registering ontology autocomplete provider...');
      // For add-route, we need to wait until the user provides a route ID
      // The autocomplete will be initialized when they start typing or we can use a placeholder
      // For now, register with a placeholder ID
      const routeId = this.createRoute.value['routeId'] || 'new-route-placeholder';
      this.initializeOntologyAutocomplete(routeId);
      console.log('[Add Route] Provider registered. Try typing "<" or pressing Ctrl+Space');
    }, 100);
  }

  toggleEditor(): void {
    this.showTemplateEditor = !this.showTemplateEditor;

    // Trigger layout update after animation completes
    if (this.showTemplateEditor && this.monacoEditor) {
      setTimeout(() => {
        this.monacoEditor.layout();
      }, 350); // Slightly longer than the 300ms transition
    }
  }

  // Method to add a new route
  addRoute(): void {
    let routeId: string = this.createRoute.value['routeId'] as string;
    let httpMethods: string[] = this.createRoute.value['routeParams'] as string[];
    let routeParams: string = 'httpMethodRestrict=' + httpMethods.join(',');
    let routeDescription: string = this.createRoute.value['routeDescription'] as string;
    let graphMartTitle: string = this.createRoute.value['graphMartUri'] as string;
    let templateBody = this.createRoute.value['template'] || ''; // Default to empty string
    let dataSourceId = this.createRoute.value['datasourceValidator'] as string;
    let graphMartUri = this.graphMarts.find(item => item.title === graphMartTitle)?.iri;
    let layers: string = this.concatenanteIris(this.layers, this.allLayers);

    // Cache parameters
    let cacheEnabled = this.createRoute.value['cacheEnabled'] as boolean;
    let cacheTtlSeconds = this.createRoute.value['cacheTtlSeconds'] as number | null;
    let cacheKeyStrategy = this.createRoute.value['cacheKeyStrategy'] as string;

    // Validate required fields (template is now optional)
    if (!routeId || !httpMethods || httpMethods.length === 0 || !routeDescription || !graphMartUri || !dataSourceId) { return; }

    // Set status to "Stopped" if template is empty, otherwise "Started"
    let status: string = templateBody.trim() === '' ? 'Stopped' : 'Started';

    // Update the autocomplete provider with the final route ID before submitting
    if (this.ontologyAutocompleteProvider) {
      this.ontologyAutocompleteProvider.setRouteId(routeId);
    }

    this.routeService.postRoute({ routeId, routeParams, dataSourceId, routeDescription, graphMartUri, templateBody, layers, status, cacheEnabled, cacheTtlSeconds, cacheKeyStrategy } as NewRoute)
      .subscribe(() => {
        this.router.navigate(['../../routes']);
        location.reload();
      });

  }

  private initializeOntologyAutocomplete(routeId: string): void {
    if (this.ontologyAutocompleteProvider) {
      // Update existing provider with new route ID
      console.log("Updated existing completion provider for route: " + routeId + "")
      this.ontologyAutocompleteProvider.setRouteId(routeId);
    } else {
      // Register new provider
      console.log("Created new completion provider for route: " + routeId + "")
      this.ontologyAutocompleteProvider = registerOntologyAutocomplete(
        this.ontologyService,
        routeId
      );
    }
    // Warm cache for better performance (only if we have a real route ID)
    if (routeId !== 'new-route-placeholder') {
      this.ontologyService.warmCache(routeId).subscribe();
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
}
