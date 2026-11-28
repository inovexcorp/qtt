/** Who may call a query route, as stored ("default" follows QUERY_AUTH_DEFAULT on the server). */
export type QueryAccess = 'default' | 'open' | 'authenticated';

/** The access that applies once the server default is taken into account. */
export function effectiveAccess(access: string | null | undefined, serverDefault: 'open' | 'authenticated'): 'open' | 'authenticated' {
  return access === 'open' || access === 'authenticated' ? access : serverDefault;
}

/**
 * A route's endpoint URL. The server returns a base URL from QUERY_BASE_URL, or "/query" when that is unset, which is
 * relative to the server the UI was loaded from.
 */
export function endpointUrl(baseUrl: string | null | undefined, routeId: string, origin: string = window.location.origin): string {
  const base = (baseUrl || '/query').replace(/\/+$/, '');
  return (base.startsWith('/') ? origin + base : base) + '/' + routeId;
}

/** A curl command that calls the route; authenticated routes read the credential from $QTT_TOKEN. */
export function curlExample(url: string, access: 'open' | 'authenticated', methods: string[] = ['GET']): string {
  const parts = ['curl'];
  const usePost = methods.length > 0 && !methods.includes('GET') && methods.includes('POST');
  if (usePost) {
    parts.push('-X POST', '-H "Content-Type: application/json"', "-d '{}'");
  }
  if (access === 'authenticated') {
    parts.push('-H "Authorization: Bearer $QTT_TOKEN"');
  }
  parts.push('"' + url + '"');
  return parts.join(' ');
}

/** HTTP methods from stored route parameters ("httpMethodRestrict=GET,POST"). */
export function methodsOf(routeParams: string | null | undefined): string[] {
  const match = /httpMethodRestrict=([^&]*)/.exec(routeParams || '');
  return match ? match[1].split(',').map(m => m.trim().toUpperCase()).filter(m => m) : [];
}
