import { HttpErrorResponse } from '@angular/common/http';

/**
 * Turns an HTTP error into a message for the user. Uses the backend's `message`, or the first
 * Bean Validation violation, and falls back to a generic text.
 */
export function errorMessage(
  error: unknown,
  fallback = 'Something went wrong, please try again.',
): string {
  if (!(error instanceof HttpErrorResponse)) {
    return fallback;
  }
  if (error.status === 0) {
    return 'Cannot reach the server.';
  }
  const body = error.error as { message?: unknown; violations?: { message?: unknown }[] } | null;
  if (body && typeof body.message === 'string') {
    return body.message;
  }
  const violation = body?.violations?.[0]?.message;
  return typeof violation === 'string' ? violation : fallback;
}
