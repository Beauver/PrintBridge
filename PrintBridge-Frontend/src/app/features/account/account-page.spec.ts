import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { User } from '../../core/models/account.model';
import { ALICE } from '../../../testing/fixtures';
import { AccountPage } from './account-page';

describe('AccountPage', () => {
  const user = signal<User | null>(ALICE);
  const auth = { user, updateAccount: vi.fn() };
  let page: AccountPage;

  function fillCurrentPassword(): void {
    page['form']().controls.currentPassword.setValue('current1');
  }

  beforeEach(() => {
    vi.clearAllMocks();
    user.set(ALICE);
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: auth }],
    });
    page = TestBed.createComponent(AccountPage).componentInstance;
  });

  it('fills in your details with the password fields empty', () => {
    expect(page['form']().getRawValue()).toEqual({
      username: 'alice',
      email: 'alice@example.com',
      password: '',
      confirmPassword: '',
      currentPassword: '',
    });
  });

  it('requires the current password', () => {
    page['save']();
    expect(auth.updateAccount).not.toHaveBeenCalled();
  });

  it('saves the details and fills in the updated ones', () => {
    const renamed = { ...ALICE, username: 'alicia' };
    auth.updateAccount.mockImplementation(() => {
      user.set(renamed);
      return of(renamed);
    });
    page['form']().controls.username.setValue('alicia');
    fillCurrentPassword();
    page['save']();

    expect(auth.updateAccount).toHaveBeenCalledWith({
      username: 'alicia',
      email: 'alice@example.com',
      changePassword: false,
      currentPassword: 'current1',
    });
    expect(page['notice']()).toBe('Your account has been updated.');
    expect(page['form']().controls.username.value).toBe('alicia');
    expect(page['form']().controls.currentPassword.value).toBe('');
    expect(page['saving']()).toBe(false);
  });

  it('says the other devices were logged out after a new password', () => {
    auth.updateAccount.mockReturnValue(of(ALICE));
    page['form']().controls.password.setValue('password1');
    page['form']().controls.confirmPassword.setValue('password1');
    fillCurrentPassword();
    page['save']();

    expect(auth.updateAccount).toHaveBeenCalledWith(
      expect.objectContaining({ changePassword: true, newPassword: 'password1' }),
    );
    expect(page['notice']()).toBe(
      'Your account has been updated. You have been logged out on other devices.',
    );
  });

  it('shows the server error', () => {
    auth.updateAccount.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { message: 'Current password is incorrect' },
          }),
      ),
    );
    fillCurrentPassword();
    page['save']();
    expect(page['error']()).toBe('Current password is incorrect');
    expect(page['saving']()).toBe(false);
  });

  it('starts empty without a logged-in user', () => {
    user.set(null);
    const empty = TestBed.createComponent(AccountPage).componentInstance;
    expect(empty['form']().controls.username.value).toBe('');
  });
});
