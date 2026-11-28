export interface NewRoute{
    routeId: string;
    routeParams: string;
    dataSourceId: string;
    routeDescription: string;
    graphMartUri: string;
    templateBody: string;
    layers: string;
    // Cache configuration
    cacheEnabled?: boolean;
    // Seconds; absent for the server default
    cacheTtlSeconds?: number | null;
    cacheKeyStrategy?: string;
    // Who may call it: "default" (QUERY_AUTH_DEFAULT), "open" or "authenticated"
    accessPolicy?: string;
    // Roles an authenticated caller must also hold, comma-separated
    requiredRoles?: string;
}