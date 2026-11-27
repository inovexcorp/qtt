import { Datasources } from "../datasources";
import { AuditInfo } from "../audit";

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
    // Who created and last changed it (read-only)
    audit?: AuditInfo | null;
  }
