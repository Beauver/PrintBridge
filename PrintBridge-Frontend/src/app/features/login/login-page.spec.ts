import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { ALICE } from '../../core/testing';
import { LoginPage } from './login-page';

describe('LoginPage', () => {
  let page: LoginPage;
  const auth = { login: vi.fn() };
  const router = { navigate: vi.fn().mockResolvedValue(true) };

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });
    page = TestBed.createComponent(LoginPage).componentInstance;
  });

  it('logs in and goes to the start page', () => {
    auth.login.mockReturnValue(of(ALICE));
    page['form'].setValue({ login: 'alice', password: 'secret' });
    page['submit']();

    expect(auth.login).toHaveBeenCalledWith('alice', 'secret');
    expect(router.navigate).toHaveBeenCalledWith(['/']);
  });

  it('requires both fields', () => {
    page['submit']();
    expect(auth.login).not.toHaveBeenCalled();
  });

  it('shows the error and clears the password on failure', () => {
    auth.login.mockReturnValue(
      throwError(
        () => new HttpErrorResponse({ status: 401, error: { message: 'Invalid credentials' } }),
      ),
    );
    page['form'].setValue({ login: 'alice', password: 'wrong' });
    page['submit']();

    expect(page['error']()).toBe('Invalid credentials');
    expect(page['form'].controls.password.value).toBe('');
    expect(page['busy']()).toBe(false);
  });
});
