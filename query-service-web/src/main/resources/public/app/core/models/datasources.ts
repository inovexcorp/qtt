export interface Datasources {
  // Array of associated route names
    camelRouteTemplate:string[];
    dataSourceId: string;
    timeOutSeconds: string;
    maxQueryHeaderLength: string;
    username: string;
    password: string;
    url: string;
    validateCertificate: boolean;

    // Health monitoring fields
    status?: string;  // 'UP' | 'DOWN' | 'UNKNOWN' | 'CHECKING' | 'DISABLED'
    lastHealthCheck?: Date;
    lastHealthError?: string;
    consecutiveFailures?: number;
  }
