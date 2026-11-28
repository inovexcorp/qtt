/** Datasource health as pushed by the server on the `datasources` topic of /api/events. */

export type DatasourceStatus = 'UP' | 'DOWN' | 'UNKNOWN' | 'CHECKING' | 'DISABLED';

/** One health check. */
export interface HealthCheck {
  status: DatasourceStatus;
  /** How long the check took; null when it failed before an answer. */
  responseTimeMs: number | null;
  errorMessage: string | null;
  /** ISO-8601 instant. */
  checkTime: string;
}

export interface DatasourceHealth {
  dataSourceId: string;
  url: string;
  status: DatasourceStatus;
  /** ISO-8601 instant; null if never checked. */
  lastHealthCheck: string | null;
  lastHealthError: string | null;
  consecutiveFailures: number;
  /** Routes using this datasource. */
  routeCount: number;
  /** The last (up to 10) checks, oldest first. */
  recentChecks: HealthCheck[];
}

export interface DatasourceHealthSnapshot {
  /** Automated health checks run on the server. */
  healthCheckEnabled: boolean;
  /** Sorted by id. */
  datasources: DatasourceHealth[];
}
