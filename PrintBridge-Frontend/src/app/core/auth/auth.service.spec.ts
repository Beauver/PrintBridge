import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ADMIN, ALICE, authResponse } from '../testing';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('logs in and stores the user and token', () => {
    let user;
    service.login('alice', 'pw').subscribe((u) => (user = u));
    const req = http.expectOne('/api/auth/login');
    expect(req.request.body).toEqual({ login: 'alice', password: 'pw' });
    req.flush(authResponse(ALICE));

    expect(user).toEqual(ALICE);
    expect(service.token()).toBe('access-token');
    expect(service.isLoggedIn()).toBe(true);
    expect(service.isAdmin()).toBe(false);
  });

  it('runs setup and logs in as admin', () => {
    service
      .setup({ username: 'admin', email: 'admin@example.com', password: 'password1' })
      .subscribe();
    http.expectOne({ method: 'POST', url: '/api/setup' }).flush(authResponse(ADMIN));
    expect(service.isAdmin()).toBe(true);
  });

  it('shares one refresh request between concurrent callers', () => {
    const results: string[] = [];
    service.refresh().subscribe((r) => results.push(r.accessToken));
    service.refresh().subscribe((r) => results.push(r.accessToken));
    http.expectOne('/api/auth/refresh').flush(authResponse(ALICE, 'new-token'));

    expect(results).toEqual(['new-token', 'new-token']);
    expect(service.token()).toBe('new-token');

    // A later refresh makes a new request.
    service.refresh().subscribe();
    http.expectOne('/api/auth/refresh').flush(authResponse(ALICE));
  });

  it('restores the session from the refresh cookie', async () => {
    const restored = service.restoreSession();
    http.expectOne('/api/auth/refresh').flush(authResponse(ALICE));
    await restored;
    expect(service.user()).toEqual(ALICE);
  });

  it('stays logged out when the refresh cookie is invalid', async () => {
    const restored = service.restoreSession();
    http
      .expectOne('/api/auth/refresh')
      .flush({ message: 'expired' }, { status: 401, statusText: 'Unauthorized' });
    await restored;
    expect(service.isLoggedIn()).toBe(false);
    expect(service.token()).toBeNull();
  });

  it('clears the session on logout, even if the call fails', () => {
    service.login('alice', 'pw').subscribe();
    http.expectOne('/api/auth/login').flush(authResponse(ALICE));

    service.logout().subscribe({ error: () => undefined });
    http.expectOne('/api/auth/logout').flush(null, { status: 500, statusText: 'Error' });
    expect(service.isLoggedIn()).toBe(false);
    expect(service.token()).toBeNull();
  });
});
