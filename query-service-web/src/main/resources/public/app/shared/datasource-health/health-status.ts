import { DatasourceStatus, HealthCheck } from '../../core/models/datasource-health';

/**
 * Words and tones for datasource health, shared by the list, the datasource page and the pill. Pure functions,
 * unit tested.
 */

/** good: up · bad: down · pending: a check is running · idle: not checked, or disabled. */
export type HealthTone = 'good' | 'bad' | 'pending' | 'idle';

export const STATUS_LABEL: Record<DatasourceStatus, string> = {
  UP: 'Up', DOWN: 'Down', UNKNOWN: 'Not checked', CHECKING: 'Checking', DISABLED: 'Disabled',
};

export function statusLabel(status: string | null | undefined): string {
  return STATUS_LABEL[status as DatasourceStatus] ?? STATUS_LABEL.UNKNOWN;
}

export function statusTone(status: string | null | undefined): HealthTone {
  switch (status) {
    case 'UP': return 'good';
    case 'DOWN': return 'bad';
    case 'CHECKING': return 'pending';
    default: return 'idle';
  }
}

/** "just now", "4 min ago", "3 h ago", "2 days ago"; older than a week: the date. '' without a time. */
export function relativeTime(iso: string | null | undefined, now: number): string {
  if (!iso) {
    return '';
  }
  const at = Date.parse(iso);
  if (Number.isNaN(at)) {
    return '';
  }
  const seconds = Math.max(0, Math.round((now - at) / 1000));
  if (seconds < 45) {
    return 'just now';
  }
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) {
    return `${minutes} min ago`;
  }
  const hours = Math.round(minutes / 60);
  if (hours < 24) {
    return `${hours} h ago`;
  }
  const days = Math.round(hours / 24);
  if (days <= 7) {
    return days === 1 ? 'yesterday' : `${days} days ago`;
  }
  return new Date(at).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}

/** Mean response time of the checks that got an answer; null if none did. */
export function averageResponseMs(checks: HealthCheck[]): number | null {
  const timed = checks.filter(c => c.status === 'UP' && c.responseTimeMs !== null);
  return timed.length ? timed.reduce((sum, c) => sum + (c.responseTimeMs ?? 0), 0) / timed.length : null;
}

/** "host:port" of a URL, for display; the URL itself if it doesn't parse. */
export function hostOf(url: string | null | undefined): string {
  if (!url) {
    return '';
  }
  try {
    return new URL(url).host || url;
  } catch {
    return url;
  }
}

/** "9 up, 1 down": the checks in words, for screen readers and tooltips. */
export function checksSummary(checks: HealthCheck[]): string {
  if (checks.length === 0) {
    return 'No health checks yet';
  }
  const down = checks.filter(c => c.status === 'DOWN').length;
  const up = checks.length - down;
  const avg = averageResponseMs(checks);
  const parts = [`${up} up`];
  if (down) {
    parts.push(`${down} down`);
  }
  return `Last ${checks.length} check${checks.length === 1 ? '' : 's'}: ${parts.join(', ')}`
    + (avg === null ? '' : `; average ${Math.round(avg)} ms`);
}
