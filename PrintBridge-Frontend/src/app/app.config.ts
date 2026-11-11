import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AuthService } from './core/auth/auth.service';
import { SetupStatusService } from './core/setup/setup-status.service';
import { ThemeService } from './core/theme/theme.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
    // Before the first route is resolved: know whether setup is needed and restore the session.
    provideAppInitializer(() => {
      inject(ThemeService);
      const setup = inject(SetupStatusService);
      const auth = inject(AuthService);
      return Promise.all([setup.load(), auth.restoreSession()]).then(() => undefined);
    }),
  ],
};
