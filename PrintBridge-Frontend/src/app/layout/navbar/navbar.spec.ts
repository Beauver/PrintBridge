import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { ALICE } from '../../core/testing';
import { Navbar } from './navbar';

describe('Navbar', () => {
  let navbar: Navbar;
  let navigate: ReturnType<typeof vi.spyOn>;
  const logout = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { user: signal(ALICE), isAdmin: signal(false), logout } },
      ],
    });
    navbar = TestBed.createComponent(Navbar).componentInstance;
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  it('opens and closes the mobile menu', () => {
    navbar['toggleMenu']();
    expect(navbar['menuOpen']()).toBe(true);
    navbar['closeMenu']();
    expect(navbar['menuOpen']()).toBe(false);
  });

  it('logs out and goes to the login page', () => {
    logout.mockReturnValue(of(undefined));
    navbar['logout']();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('goes to the login page even if logout fails', () => {
    logout.mockReturnValue(throwError(() => new Error('offline')));
    navbar['logout']();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });
});
