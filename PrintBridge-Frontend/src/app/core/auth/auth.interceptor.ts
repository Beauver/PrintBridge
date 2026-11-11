import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** Endpoints that work without (or manage) the access token themselves. */
const PUBLIC_ENDPOINTS = ['/api/auth/login', '/api/auth/refresh', '/api/auth/logout', '/api/setup'];

function withToken(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
}

/**
 * Adds the access token to API calls. On a 401 it refreshes once and retries; if the refresh
 * fails too, the session is over and the user is sent to the login page.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/') || PUBLIC_ENDPOINTS.some((url) => req.url.startsWith(url))) {
    return next(req);
  }
  const auth = inject(AuthService);
  const router = inject(Router);

  return next(withToken(req, auth.token())).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
        return throwError(() => error);
      }
      return auth.refresh().pipe(
        catchError(() => {
          auth.clearSession();
          void router.navigate(['/login']);
          return throwError(() => error);
        }),
        switchMap(() => next(withToken(req, auth.token()))),
      );
    }),
  );
};
