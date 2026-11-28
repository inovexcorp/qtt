import { AuditInfo } from './audit';

export interface Datasources {
  // Array of associated route names
    camelRouteTemplate:string[];
    dataSourceId: string;
    timeOutSeconds: string;
    maxQueryHeaderLength: string;
    username: string;
    // Write-only: the API never returns it. Leave it out of an update to keep the stored password.
    password?: string;
    url: string;
    validateCertificate: boolean;

    // Health monitoring fields
    status?: string;  // 'UP' | 'DOWN' | 'UNKNOWN' | 'CHECKING' | 'DISABLED'
    lastHealthCheck?: Date;
    lastHealthError?: string;
    consecutiveFailures?: number;

    // Who created and last changed it (read-only)
    audit?: AuditInfo | null;
  }
