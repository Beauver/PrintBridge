import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { SetupStatusService } from '../../core/setup/setup-status.service';
import { ADMIN } from '../../core/testing';
import { SetupPage } from './setup-page';

describe('SetupPage', () => {
  let page: SetupPage;
  const auth = { setup: vi.fn() };
  const setupStatus = { markDone: vi.fn() };
  const router = { navigate: vi.fn().mockResolvedValue(true) };

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: SetupStatusService, useValue: setupStatus },
        { provide: Router, useValue: router },
      ],
    });
    page = TestBed.createComponent(SetupPage).componentInstance;
  });

  function fill(confirmPassword = 'password123'): void {
    page['form'].setValue({
      username: 'owner',
      email: 'owner@example.com',
      password: 'password123',
      confirmPassword,
    });
  }

  it('creates the admin, marks setup done and goes to the start page', () => {
    auth.setup.mockReturnValue(of(ADMIN));
    fill();
    page['submit']();

    expect(auth.setup).toHaveBeenCalledWith({
      username: 'owner',
      email: 'owner@example.com',
      password: 'password123',
    });
    expect(setupStatus.markDone).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/']);
  });

  it('does not submit when passwords differ', () => {
    fill('different1');
    page['submit']();

    expect(page['form'].hasError('passwordMismatch')).toBe(true);
    expect(auth.setup).not.toHaveBeenCalled();
  });

  it('does not submit an empty form and marks the fields touched', () => {
    page['submit']();

    expect(auth.setup).not.toHaveBeenCalled();
    expect(page['form'].controls.username.touched).toBe(true);
  });

  it('shows the server error and allows a retry', () => {
    auth.setup.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { message: 'Setup has already been completed' },
          }),
      ),
    );
    fill();
    page['submit']();

    expect(page['error']()).toBe('Setup has already been completed');
    expect(page['busy']()).toBe(false);
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
