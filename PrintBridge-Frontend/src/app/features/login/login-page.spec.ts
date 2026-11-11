import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { ALICE } from '../../core/testing';
import { byCy, submit, type } from '../../core/testing-dom';
import { LoginPage } from './login-page';

describe('LoginPage', () => {
  let fixture: ComponentFixture<LoginPage>;
  const auth = { login: vi.fn() };
  const router = { navigate: vi.fn().mockResolvedValue(true) };

  beforeEach(async () => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });
    fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();
  });

  it('logs in and goes to the start page', async () => {
    auth.login.mockReturnValue(of(ALICE));
    type(fixture, 'login-name', 'alice');
    type(fixture, 'login-password', 'secret');
    submit(fixture, 'login-form');
    await fixture.whenStable();

    expect(auth.login).toHaveBeenCalledWith('alice', 'secret');
    expect(router.navigate).toHaveBeenCalledWith(['/']);
  });

  it('requires both fields', async () => {
    submit(fixture, 'login-form');
    await fixture.whenStable();
    expect(auth.login).not.toHaveBeenCalled();
    expect(byCy(fixture, 'login-name')!.classList).toContain('is-invalid');
  });

  it('shows invalid credentials and clears the password', async () => {
    auth.login.mockReturnValue(
      throwError(
        () => new HttpErrorResponse({ status: 401, error: { message: 'Invalid credentials' } }),
      ),
    );
    type(fixture, 'login-name', 'alice');
    type(fixture, 'login-password', 'wrong');
    submit(fixture, 'login-form');
    await fixture.whenStable();

    expect(byCy(fixture, 'login-error')!.textContent).toContain('Invalid credentials');
    expect(byCy<HTMLInputElement>(fixture, 'login-password')!.value).toBe('');
  });
});
