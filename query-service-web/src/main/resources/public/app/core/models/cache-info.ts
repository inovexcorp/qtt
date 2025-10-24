export interface CacheInfo {
  enabled: boolean;
  connected: boolean;
  type: string;
  host: string;
  port: number;
  database: number;
  keyPrefix: string;
  defaultTtlSeconds: number;
  compressionEnabled: boolean;
  failOpen: boolean;
  errorMessage?: string;
}

export interface CacheStats {
  hits: number;
  misses: number;
  errors: number;
  evictions: number;
  keyCount: number;
  memoryUsageBytes: number;
}

export interface RouteCacheStats {
  routeId: string;
  cacheEnabled: boolean;
  cacheTtlSeconds: number | null;
  routeKeyCount: number;
  globalStats: CacheStats;
}
