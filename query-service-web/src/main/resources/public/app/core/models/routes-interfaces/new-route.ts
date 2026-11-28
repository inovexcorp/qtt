export interface NewRoute{
    routeId: string;
    routeParams: string;
    dataSourceId: string;
    routeDescription: string;
    graphMartUri: string;
    templateBody: string;
    layers: string;
    status?: string;
    // Cache configuration
    cacheEnabled?: boolean;
    cacheTtlSeconds?: number;
    cacheKeyStrategy?: string;
    // Who may call it: "default" (QUERY_AUTH_DEFAULT), "open" or "authenticated"
    accessPolicy?: string;
    // Roles an authenticated caller must also hold, comma-separated
    requiredRoles?: string;
}