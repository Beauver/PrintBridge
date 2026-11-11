import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { AuthService } from './core/auth/auth.service';

describe('App', () => {
  it('creates', () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: { isLoggedIn: signal(false), isAdmin: signal(false), user: signal(null) },
        },
      ],
    });
    expect(TestBed.createComponent(App).componentInstance).toBeTruthy();
  });
});
