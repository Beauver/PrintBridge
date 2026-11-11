import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { finalize, firstValueFrom, map, Observable, shareReplay, tap } from 'rxjs';
import { AuthResponse, NewAccount, User } from '../models';

/**
 * Holds the logged-in user and the access token (in memory only). The refresh token is an
 * httpOnly cookie the browser sends to /api/auth itself, so a page reload restores the session.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly currentUser = signal<User | null>(null);
  private accessToken: string | null = null;
  private refreshInFlight: Observable<AuthResponse> | null = null;

  readonly user = this.currentUser.asReadonly();
  readonly isLoggedIn = computed(() => this.currentUser() !== null);
  readonly isAdmin = computed(() => this.currentUser()?.role === 'ADMIN');

  token(): string | null {
    return this.accessToken;
  }

  login(login: string, password: string): Observable<User> {
    return this.http.post<AuthResponse>('/api/auth/login', { login, password }).pipe(
      tap((response) => this.store(response)),
      map((response) => response.user),
    );
  }

  /** First-run setup: creates the admin account and logs it in. */
  setup(account: NewAccount): Observable<User> {
    return this.http.post<AuthResponse>('/api/setup', account).pipe(
      tap((response) => this.store(response)),
      map((response) => response.user),
    );
  }

  /** Gets a new access token. Concurrent callers share one request, because each refresh rotates the cookie. */
  refresh(): Observable<AuthResponse> {
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.http.post<AuthResponse>('/api/auth/refresh', null).pipe(
        tap((response) => this.store(response)),
        finalize(() => (this.refreshInFlight = null)),
        shareReplay(1),
      );
    }
    return this.refreshInFlight;
  }

  /** Called on startup: logs the user back in if the refresh cookie is still valid. */
  async restoreSession(): Promise<void> {
    try {
      await firstValueFrom(this.refresh());
    } catch {
      this.clearSession();
    }
  }

  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', null).pipe(finalize(() => this.clearSession()));
  }

  clearSession(): void {
    this.accessToken = null;
    this.currentUser.set(null);
  }

  private store(response: AuthResponse): void {
    this.accessToken = response.accessToken;
    this.currentUser.set(response.user);
  }
}
