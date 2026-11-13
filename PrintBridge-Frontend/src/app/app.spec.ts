import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { of } from 'rxjs';
import { AboutService } from './core/about/about.service';
import { AuthService } from './core/auth/auth.service';

describe('App', () => {
  it('creates', () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AboutService,
          useValue: {
            get: () => {
              return of({ sourceUrl: 'x', licence: 'y' });
            },
          },
        },
        {
          provide: AuthService,
          useValue: { isLoggedIn: signal(false), isAdmin: signal(false), user: signal(null) },
        },
      ],
    });
    expect(TestBed.createComponent(App).componentInstance).toBeTruthy();
  });
});
