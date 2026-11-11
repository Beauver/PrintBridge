import { Routes } from '@angular/router';
import {
  adminGuard,
  authGuard,
  guestGuard,
  setupDoneGuard,
  setupPageGuard,
} from './core/auth/guards';

export const routes: Routes = [
  {
    path: 'setup',
    canActivate: [setupPageGuard],
    title: 'Set up PrintBridge',
    loadComponent: () => import('./features/setup/setup-page').then((m) => m.SetupPage),
  },
  {
    path: 'login',
    canActivate: [setupDoneGuard, guestGuard],
    title: 'Log in · PrintBridge',
    loadComponent: () => import('./features/login/login-page').then((m) => m.LoginPage),
  },
  {
    path: '',
    canActivate: [setupDoneGuard, authGuard],
    children: [
      {
        path: '',
        title: 'PrintBridge',
        loadComponent: () => import('./features/home/home-page').then((m) => m.HomePage),
      },
      {
        path: 'admin/users',
        canActivate: [adminGuard],
        title: 'Users · PrintBridge',
        loadComponent: () => import('./features/users/users-page').then((m) => m.UsersPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
