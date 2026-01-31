import { Datasources } from "../datasources";

export interface Routes {
    routeId: string;
    routeParams: string;
    templateContent: string;
    description: string;
    graphMartUri: string;
    status: string;
    datasources: Datasources;
    // Cache configuration
    cacheEnabled?: boolean;
    cacheTtlSeconds?: number;
    cacheKeyStrategy?: string;
    // Authentication configuration
    bearerAuthEnabled?: boolean;
  }
