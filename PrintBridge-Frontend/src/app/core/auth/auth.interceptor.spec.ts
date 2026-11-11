import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { ALICE, authResponse } from '../testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let auth: AuthService;
  let navigate: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    navigate = vi.fn().mockResolvedValue(true);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigate } },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);

    auth.login('alice', 'pw').subscribe();
    backend.expectOne('/api/auth/login').flush(authResponse(ALICE, 'token-1'));
  });

  afterEach(() => backend.verify());

  it('adds the access token to API calls', () => {
    http.get('/api/users').subscribe();
    expect(backend.expectOne('/api/users').request.headers.get('Authorization')).toBe(
      'Bearer token-1',
    );
  });

  it('leaves non-API and public calls alone', () => {
    http.get('/assets/x.json').subscribe();
    http.post('/api/auth/refresh', null).subscribe();
    expect(backend.expectOne('/assets/x.json').request.headers.has('Authorization')).toBe(false);
    expect(backend.expectOne('/api/auth/refresh').request.headers.has('Authorization')).toBe(false);
  });

  it('refreshes once on 401 and retries with the new token', () => {
    let result: unknown;
    http.get('/api/users').subscribe((r) => (result = r));
    backend.expectOne('/api/users').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/auth/refresh').flush(authResponse(ALICE, 'token-2'));

    const retry = backend.expectOne('/api/users');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer token-2');
    retry.flush(['ok']);
    expect(result).toEqual(['ok']);
  });

  it('logs out and goes to login when the refresh fails', () => {
    let status: number | undefined;
    http.get('/api/users').subscribe({ error: (e) => (status = e.status) });
    backend.expectOne('/api/users').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(status).toBe(401);
    expect(auth.isLoggedIn()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('passes other errors through', () => {
    let status: number | undefined;
    http.get('/api/users').subscribe({ error: (e) => (status = e.status) });
    backend.expectOne('/api/users').flush(null, { status: 403, statusText: 'Forbidden' });
    expect(status).toBe(403);
  });
});
