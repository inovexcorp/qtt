/** Who created and last changed a route or datasource through the API (absent for rows older than sign-in support). */
export interface AuditInfo {
  createdBy?: string | null;
  createdAt?: number | string | null;
  modifiedBy?: string | null;
  modifiedAt?: number | string | null;
}
