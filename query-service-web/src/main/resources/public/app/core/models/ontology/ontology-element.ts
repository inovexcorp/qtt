/**
 * Represents an element from an ontology (class, property, or individual).
 * Used for URI autocomplete functionality in the template editor.
 */
export interface OntologyElement {
  uri: string;
  label: string;
  type: OntologyElementType;
  description?: string;

  /**
   * Domain URIs for properties (rdfs:domain values, excluding blank nodes).
   * Only populated for property types. Empty/undefined for classes and individuals.
   *
   * Note: Currently only includes URI-based domains. Complex class expressions
   * (owl:unionOf, owl:intersectionOf) from blank nodes are not yet parsed.
   */
  domains?: string[];

  /**
   * Range URIs for properties (rdfs:range values, excluding blank nodes).
   * Only populated for property types. Empty/undefined for classes and individuals.
   *
   * Note: Currently only includes URI-based ranges. Complex class expressions
   * (owl:unionOf, owl:intersectionOf) from blank nodes are not yet parsed.
   */
  ranges?: string[];
}

/**
 * Types of ontology elements that can be queried.
 */
export type OntologyElementType =
  | 'class'
  | 'objectProperty'
  | 'datatypeProperty'
  | 'annotationProperty'
  | 'individual'
  | 'all';

/**
 * Metadata about cached ontology data for a specific route.
 */
export interface OntologyMetadata {
  routeId: string;
  graphmartUri: string;
  layerUris: string;
  elementCount: number;
  lastUpdated: string;
  cached: boolean;
  status: string;
}

/**
 * Statistics about the ontology cache performance.
 */
export interface CacheStatistics {
  hitCount: number;
  missCount: number;
  totalLoadTime: number;
  evictionCount: number;
  size: number;
  hitRate: number;
}

/**
 * Parameters for searching ontology elements.
 */
export interface OntologySearchParams {
  routeId: string;
  type?: OntologyElementType;
  prefix?: string;
  limit?: number;
}
