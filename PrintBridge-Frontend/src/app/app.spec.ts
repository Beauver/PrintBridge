import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { AuthService } from './core/auth/auth.service';
import { ALICE } from './core/testing';

describe('App', () => {
  function render(loggedIn: boolean) {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            isLoggedIn: signal(loggedIn),
            isAdmin: signal(false),
            user: signal(loggedIn ? ALICE : null),
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the navbar only when logged in', () => {
    expect(render(false).querySelector('app-navbar')).toBeNull();
    TestBed.resetTestingModule();
    expect(render(true).querySelector('app-navbar')).not.toBeNull();
  });

  it('always shows the theme toggle', () => {
    expect(render(false).querySelector('app-theme-toggle')).not.toBeNull();
  });
});
