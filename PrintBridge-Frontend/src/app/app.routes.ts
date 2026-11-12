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
        title: 'My requests · PrintBridge',
        loadComponent: () =>
          import('./features/requests/my-requests/my-requests-page').then((m) => m.MyRequestsPage),
      },
      {
        path: 'requests/new',
        title: 'New request · PrintBridge',
        loadComponent: () =>
          import('./features/requests/new-request/new-request-page').then((m) => m.NewRequestPage),
      },
      {
        path: 'requests/:id',
        title: 'Request · PrintBridge',
        loadComponent: () =>
          import('./features/requests/request-detail/request-detail-page').then(
            (m) => m.RequestDetailPage,
          ),
      },
      {
        path: 'admin/requests',
        canActivate: [adminGuard],
        title: 'All requests · PrintBridge',
        loadComponent: () =>
          import('./features/requests/all-requests/all-requests-page').then(
            (m) => m.AllRequestsPage,
          ),
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
