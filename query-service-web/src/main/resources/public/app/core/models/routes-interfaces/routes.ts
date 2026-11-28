import { Datasources } from "../datasources";
import { AuditInfo } from "../audit";

/** Why a route is stopped: someone stopped it, the datasource health check did, or it has no template. */
export type RouteStopReason = 'MANUAL' | 'HEALTH_CHECK' | 'NO_TEMPLATE';

export interface Routes {
    routeId: string;
    routeParams: string;
    templateContent: string;
    description: string;
    graphMartUri: string;
    status: string;
    /** Why it is stopped (null while it runs, and on routes stopped before reasons were recorded). */
    stopReason?: RouteStopReason | null;
    /** When it was stopped (ms since epoch); null while it runs. */
    stoppedAt?: number | string | null;
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
