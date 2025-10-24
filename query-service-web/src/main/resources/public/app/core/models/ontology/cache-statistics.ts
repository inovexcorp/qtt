export interface CacheStatistics {
  hitCount: number;
  missCount: number;
  totalLoadTime: number;
  evictionCount: number;
  size: number;
  hitRate: number;
}
