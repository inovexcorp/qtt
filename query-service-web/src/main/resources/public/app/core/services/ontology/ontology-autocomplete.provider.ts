import * as monaco from 'monaco-editor';
import {OntologyService} from './ontology.service';
import {OntologyElement} from '../../models/ontology/ontology-element';

/**
 * Monaco Editor completion provider for ontology URI autocomplete.
 * Provides intelligent suggestions for URIs from the ontology closure
 * based on the route's graphmart and layer configuration.
 */
export class OntologyAutocompleteProvider implements monaco.languages.CompletionItemProvider {
  private routeId: string;

  constructor(
    private ontologyService: OntologyService,
    routeId: string
  ) {
    this.routeId = routeId;
  }

  /**
   * Update the route ID when editing a different route's template.
   */
  setRouteId(routeId: string): void {
    this.routeId = routeId;
  }

  /**
   * Provide completion items based on cursor position and current word.
   */
  provideCompletionItems(model: monaco.editor.ITextModel,
                               position: monaco.Position,
                               context: monaco.languages.CompletionContext): monaco.languages.ProviderResult<monaco.languages.CompletionList> {
    // Extract text until cursor for context analysis
    const textUntilPosition = model.getValueInRange({
      startLineNumber: position.lineNumber,
      startColumn: 1,
      endLineNumber: position.lineNumber,
      endColumn: position.column
    });

    // Get full line to find text after cursor as well
    const fullLine = model.getLineContent(position.lineNumber);
    const textAfterPosition = fullLine.substring(position.column - 1);

    // Determine if we're inside a URI context (between < > or quotes)
    const isInUriContext = this.isInUriContext(textUntilPosition);

    // Check if we're using a prefixed name (e.g., ex:Person)
    const isPrefixedName = this.isPrefixedName(textUntilPosition);

    // Calculate range and search prefix based on context
    let range: monaco.IRange;
    let searchPrefix = '';

    if (isInUriContext) {
      // In URI context, get everything after the opening bracket
      const match = textUntilPosition.match(/[<"']([^<>"']*)$/);
      if (match) {
        searchPrefix = match[1];
        // Calculate start column: position of opening bracket + 1
        const startColumn = position.column - searchPrefix.length;
        // Calculate end column: find closing bracket or end of URI
        const afterMatch = textAfterPosition.match(/^([^<>"'\s]*)/);
        const textAfterCursor = afterMatch ? afterMatch[1] : '';
        const endColumn = position.column + textAfterCursor.length;

        range = {
          startLineNumber: position.lineNumber,
          endLineNumber: position.lineNumber,
          startColumn: startColumn,
          endColumn: endColumn
        };
      } else {
        // Fallback to default word-based range
        const word = model.getWordUntilPosition(position);
        range = {
          startLineNumber: position.lineNumber,
          endLineNumber: position.lineNumber,
          startColumn: word.startColumn,
          endColumn: word.endColumn
        };
      }
    } else if (isPrefixedName) {
      // For prefixed names, get the prefix and local part (supports default namespace ":")
      const match = textUntilPosition.match(/(?:^|[^\w:/])(\w*):(\w*)$/);
      if (match) {
        const prefix = match[1]; // e.g., "skos" or "" for default namespace
        const localPart = match[2]; // e.g., "Conc" or ""
        searchPrefix = localPart || prefix; // Search using the local part, or prefix if empty

        // Calculate range to replace ONLY the local part (keep the prefix:)
        // Start from after the colon
        const startColumn = position.column - localPart.length;

        // Find end of word after cursor
        const afterMatch = textAfterPosition.match(/^(\w*)/);
        const textAfterCursor = afterMatch ? afterMatch[1] : '';
        const endColumn = position.column + textAfterCursor.length;

        range = {
          startLineNumber: position.lineNumber,
          endLineNumber: position.lineNumber,
          startColumn: startColumn,
          endColumn: endColumn,
          // Store the prefix for later use in completion item creation
          __prefix: prefix
        } as any;
      } else {
        const word = model.getWordUntilPosition(position);
        range = {
          startLineNumber: position.lineNumber,
          endLineNumber: position.lineNumber,
          startColumn: word.startColumn,
          endColumn: word.endColumn
        };
      }
    } else {
      // Default word-based range
      const word = model.getWordUntilPosition(position);
      searchPrefix = word.word || '';
      range = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn
      };
    }

    // Don't trigger unless we have meaningful input
    // For prefixed names, allow empty search (e.g., "skos:" with no local part yet)
    if (!isInUriContext && !isPrefixedName && searchPrefix.length < 2) {
      return undefined;
    }

    // For prefixed names with empty local part, still search (e.g., "skos:" or ":" should show elements)
    if (isPrefixedName && searchPrefix.length === 0) {
      // Get the prefix to filter by namespace (supports default namespace ":")
      const match = textUntilPosition.match(/(?:^|[^\w:/])(\w*):$/);
      if (match) {
        searchPrefix = match[1] || ''; // Use the prefix as search term, or empty for default namespace
      }
    }

    // Search for matching ontology elements
    return this.ontologyService.searchOntologyElements(
      this.routeId,
      searchPrefix,
      'all',
      50
    ).toPromise().then(elements => {
      if (!elements || elements.length === 0) {
        return { suggestions: [] };
      }

      // Convert ontology elements to Monaco completion items
      const suggestions: monaco.languages.CompletionItem[] = elements.map(element =>
        this.createCompletionItem(element, range, isInUriContext, isPrefixedName)
      );

      return {
        suggestions: suggestions,
        incomplete: elements.length >= 50 // Indicate there might be more results
      };
    }).catch(error => {
      console.error('[Ontology Autocomplete] Error:', error);
      return { suggestions: [] };
    });
  }

  private localName(uri: string): string | undefined{
    return uri.split('#').pop() || uri.split('/').pop();
  }

  /**
   * Create a Monaco completion item from an ontology element.
   */
  private createCompletionItem(
    element: OntologyElement,
    range: monaco.IRange,
    isInUriContext: boolean,
    isPrefixedName: boolean
  ): monaco.languages.CompletionItem {
    // Format the insert text based on context
    let insertText: string;
    let label: string;

    if (isInUriContext) {
      // Inside brackets/quotes, just insert the URI
      insertText = element.uri + ">";
      label = element.label;
    } else if (isPrefixedName) {
      // For prefixed names, just insert the local name (label)
      // User typed "skos:Conc" -> replace with "Concept" -> becomes "skos:Concept"
      // Extract just the label without any prefix or URI parts
      insertText = this.localName(element.uri) || element.label;
      label = element.label;
    } else {
      // Outside any context, wrap in brackets
      insertText = `<${element.uri}>`;
      label = element.label;
    }

    // Determine icon kind based on element type
    const kind = this.getCompletionKind(element.type);

    return {
      label: label,
      kind: kind,
      detail: element.uri,
      documentation: element.description || `${this.getTypeLabel(element.type)} from ontology`,
      insertText: insertText,
      range: range,
      sortText: `0${element.label}`, // Ensure ontology suggestions appear near the top
      filterText: element.label // Filter by label for easier matching
    };
  }

  /**
   * Determine if the cursor is inside a URI context (between < > or quotes).
   */
  private isInUriContext(textUntilPosition: string): boolean {
    // Check for < without closing >
    const lastOpenAngle = textUntilPosition.lastIndexOf('<');
    const lastCloseAngle = textUntilPosition.lastIndexOf('>');
    if (lastOpenAngle > lastCloseAngle) {
      return true;
    }

    // Check for quotes (single or double)
    const singleQuotes = (textUntilPosition.match(/'/g) || []).length;
    const doubleQuotes = (textUntilPosition.match(/"/g) || []).length;

    return (singleQuotes % 2 === 1) || (doubleQuotes % 2 === 1);
  }

  /**
   * Determine if we're typing a prefixed name (e.g., ex:Person, skos:Concept, :localName).
   */
  private isPrefixedName(textUntilPosition: string): boolean {
    // Look for pattern like "word:" or ":" at the end, not inside brackets/quotes
    // Check we're not in a URI context first
    if (this.isInUriContext(textUntilPosition)) {
      return false;
    }
    // Match PREFIX:localName pattern (including default namespace :localName)
    // Also check it's not part of a URI like http://
    return /(?:^|[^\w:/])(\w*):(\w*)$/.test(textUntilPosition) &&
           !textUntilPosition.match(/https?:\/\/[^\s]*$/);
  }

  /**
   * Map ontology element type to Monaco completion item kind.
   */
  private getCompletionKind(type: string): monaco.languages.CompletionItemKind {
    switch (type) {
      case 'class':
        return monaco.languages.CompletionItemKind.Class;
      case 'objectProperty':
      case 'datatypeProperty':
      case 'annotationProperty':
        return monaco.languages.CompletionItemKind.Property;
      case 'individual':
        return monaco.languages.CompletionItemKind.Value;
      default:
        return monaco.languages.CompletionItemKind.Reference;
    }
  }

  /**
   * Get human-readable label for element type.
   */
  private getTypeLabel(type: string): string {
    const labels: Record<string, string> = {
      'class': 'Class',
      'objectProperty': 'Object Property',
      'datatypeProperty': 'Datatype Property',
      'annotationProperty': 'Annotation Property',
      'individual': 'Individual'
    };
    return labels[type] || type;
  }
}

/**
 * Register the ontology autocomplete provider with Monaco Editor.
 * Should be called once during application initialization.
 */
export function registerOntologyAutocomplete(
  ontologyService: OntologyService,
  routeId: string
): OntologyAutocompleteProvider {
  const provider = new OntologyAutocompleteProvider(ontologyService, routeId);

  // Register for freemarker2 language
  monaco.languages.registerCompletionItemProvider('freemarker2', {
    triggerCharacters: ['<', ':', '/'],
    provideCompletionItems: (model, position, context) => {
      return provider.provideCompletionItems(model, position, context);
    }
  });

  return provider;
}
