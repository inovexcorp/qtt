import { averageResponseMs, checksSummary, hostOf, relativeTime, statusLabel, statusTone } from './health-status';
import { HealthCheck } from '../../core/models/datasource-health';

const up = (ms: number): HealthCheck => ({ status: 'UP', responseTimeMs: ms, errorMessage: null, checkTime: '2026-11-28T12:00:00Z' });
const down: HealthCheck = { status: 'DOWN', responseTimeMs: null, errorMessage: 'refused', checkTime: '2026-11-28T12:02:00Z' };

describe('health-status', () => {
  it('names and tones every status, unknown values as not checked', () => {
    expect(statusLabel('UNKNOWN')).toBe('Not checked');
    expect(statusLabel(undefined)).toBe('Not checked');
    expect(statusTone('UP')).toBe('good');
    expect(statusTone('DOWN')).toBe('bad');
    expect(statusTone('CHECKING')).toBe('pending');
    expect(statusTone('DISABLED')).toBe('idle');
  });

  it('says how long ago', () => {
    const now = Date.parse('2026-11-28T12:00:00Z');
    expect(relativeTime('2026-11-28T11:59:40Z', now)).toBe('just now');
    expect(relativeTime('2026-11-28T11:56:00Z', now)).toBe('4 min ago');
    expect(relativeTime('2026-11-28T09:00:00Z', now)).toBe('3 h ago');
    expect(relativeTime('2026-11-27T10:00:00Z', now)).toBe('yesterday');
    expect(relativeTime(null, now)).toBe('');
  });

  it('averages only checks that answered', () => {
    expect(averageResponseMs([up(100), down, up(200)])).toBe(150);
    expect(averageResponseMs([down])).toBeNull();
  });

  it('summarises checks in words', () => {
    expect(checksSummary([up(100), down])).toBe('Last 2 checks: 1 up, 1 down; average 100 ms');
    expect(checksSummary([])).toBe('No health checks yet');
  });

  it('shows a URL by host and port', () => {
    expect(hostOf('https://anzo.example.com:8443/path')).toBe('anzo.example.com:8443');
    expect(hostOf('not a url')).toBe('not a url');
  });
});
