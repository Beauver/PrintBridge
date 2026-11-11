import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SetupStatusService } from '../setup/setup-status.service';
import { AuthService } from './auth.service';

/** Everything except the setup page is unreachable until the admin account exists. */
export const setupDoneGuard: CanActivateFn = () =>
  inject(SetupStatusService).required() ? inject(Router).createUrlTree(['/setup']) : true;

/** The setup page is only reachable while no admin exists. */
export const setupPageGuard: CanActivateFn = () =>
  inject(SetupStatusService).required() ? true : inject(Router).createUrlTree(['/login']);

export const authGuard: CanActivateFn = () =>
  inject(AuthService).isLoggedIn() ? true : inject(Router).createUrlTree(['/login']);

/** Login page: already logged-in users go to the start page. */
export const guestGuard: CanActivateFn = () =>
  inject(AuthService).isLoggedIn() ? inject(Router).createUrlTree(['/']) : true;

export const adminGuard: CanActivateFn = () =>
  inject(AuthService).isAdmin() ? true : inject(Router).createUrlTree(['/']);
