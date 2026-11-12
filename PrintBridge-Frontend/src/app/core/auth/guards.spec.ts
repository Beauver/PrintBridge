import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { signal } from '@angular/core';
import { SetupStatusService } from '../setup/setup-status.service';
import { AuthService } from './auth.service';
import { adminGuard, authGuard, guestGuard, setupDoneGuard, setupPageGuard } from './guards';
import { CanActivateFn } from '@angular/router';

describe('guards', () => {
  const setupRequired = signal(false);
  const loggedIn = signal(false);
  const admin = signal(false);

  beforeEach(() => {
    setupRequired.set(false);
    loggedIn.set(false);
    admin.set(false);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: SetupStatusService, useValue: { required: setupRequired } },
        { provide: AuthService, useValue: { isLoggedIn: loggedIn, isAdmin: admin } },
      ],
    });
  });

  function run(guard: CanActivateFn): true | string {
    const result = TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
    return result instanceof UrlTree
      ? TestBed.inject(Router).serializeUrl(result)
      : (result as true);
  }

  it('sends everything to setup while setup is required', () => {
    setupRequired.set(true);
    expect(run(setupDoneGuard)).toBe('/setup');
    expect(run(setupPageGuard)).toBe(true);
  });

  it('locks the setup page once done', () => {
    expect(run(setupDoneGuard)).toBe(true);
    expect(run(setupPageGuard)).toBe('/login');
  });

  it('requires login', () => {
    expect(run(authGuard)).toBe('/login');
    expect(run(guestGuard)).toBe(true);
    loggedIn.set(true);
    expect(run(authGuard)).toBe(true);
    expect(run(guestGuard)).toBe('/');
  });

  it('requires the admin role', () => {
    expect(run(adminGuard)).toBe('/');
    admin.set(true);
    expect(run(adminGuard)).toBe(true);
  });
});
