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
    // Who may call it: null (server default), "open" or "authenticated"; required roles, comma-separated
    accessPolicy?: string | null;
    requiredRoles?: string | null;
    // Who created and last changed it (read-only)
    audit?: AuditInfo | null;
  }
