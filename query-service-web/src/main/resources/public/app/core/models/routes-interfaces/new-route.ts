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
}