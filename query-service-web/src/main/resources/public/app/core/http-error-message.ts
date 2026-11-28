/** A readable reason from a failed save (JSON error with a message, or plain text, or the HTTP message). */
export function saveErrorMessage(error: any): string {
  const body = error?.error;
  if (body && typeof body === 'object' && body.message) {
    return body.message;
  }
  if (typeof body === 'string' && body.trim()) {
    try {
      const parsed = JSON.parse(body);
      if (parsed?.message) {
        return parsed.message;
      }
    } catch {
      // plain text
    }
    return body.trim();
  }
  return error?.message || 'unknown error';
}
