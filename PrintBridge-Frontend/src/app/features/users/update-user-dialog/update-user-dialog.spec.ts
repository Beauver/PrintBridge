import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ADMIN, ALICE } from '../../../../testing/fixtures';
import { UsersService } from '../users.service';
import { UpdateUserDialog } from './update-user-dialog';

describe('UpdateUserDialog', () => {
  const modal = { close: vi.fn(), dismiss: vi.fn() };
  const usersService = { update: vi.fn() };
  const auth = { user: signal(ADMIN), updateAccount: vi.fn() };
  let dialog: UpdateUserDialog;

  function open(user = ALICE): UpdateUserDialog {
    const created = TestBed.createComponent(UpdateUserDialog).componentInstance;
    created.setUser(user);
    return created;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        { provide: NgbActiveModal, useValue: modal },
        { provide: UsersService, useValue: usersService },
        { provide: AuthService, useValue: auth },
      ],
    });
  });

  describe('for another user', () => {
    beforeEach(() => {
      dialog = open();
    });

    it('fills in the details and does not ask for the current password', () => {
      expect(dialog['ownAccount']()).toBe(false);
      expect(dialog['form']().controls.username.value).toBe('alice');
      expect(dialog['form']().controls.email.value).toBe('alice@example.com');
      expect(dialog['form']().controls.password.value).toBe('');
    });

    it('saves the details and closes', () => {
      const renamed = { ...ALICE, username: 'alicia' };
      usersService.update.mockReturnValue(of(renamed));
      dialog['form']().controls.username.setValue('alicia');
      dialog['save']();

      expect(usersService.update).toHaveBeenCalledWith(2, {
        username: 'alicia',
        email: 'alice@example.com',
        changePassword: false,
      });
      expect(modal.close).toHaveBeenCalledWith({ user: renamed, passwordChanged: false });
    });

    it('sends a new password', () => {
      usersService.update.mockReturnValue(of(ALICE));
      dialog['form']().controls.password.setValue('password1');
      dialog['form']().controls.confirmPassword.setValue('password1');
      dialog['save']();

      expect(usersService.update).toHaveBeenCalledWith(2, {
        username: 'alice',
        email: 'alice@example.com',
        changePassword: true,
        newPassword: 'password1',
      });
      expect(modal.close).toHaveBeenCalledWith({ user: ALICE, passwordChanged: true });
    });

    it('does not submit an invalid form', () => {
      dialog['form']().controls.password.setValue('password1');
      dialog['save']();
      expect(usersService.update).not.toHaveBeenCalled();
    });

    it('shows the server error and stays open', () => {
      usersService.update.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({ status: 409, error: { message: 'Username is already taken' } }),
        ),
      );
      dialog['save']();
      expect(dialog['saveError']()).toBe('Username is already taken');
      expect(dialog['saving']()).toBe(false);
      expect(modal.close).not.toHaveBeenCalled();
    });

    it('can be cancelled', () => {
      dialog['cancel']();
      expect(modal.dismiss).toHaveBeenCalled();
    });
  });

  describe('for your own account', () => {
    beforeEach(() => {
      dialog = open(ADMIN);
    });

    it('requires the current password', () => {
      expect(dialog['ownAccount']()).toBe(true);
      dialog['save']();
      expect(auth.updateAccount).not.toHaveBeenCalled();
    });

    it('saves through the own-account endpoint', () => {
      auth.updateAccount.mockReturnValue(of(ADMIN));
      dialog['form']().controls.currentPassword.setValue('current1');
      dialog['save']();

      expect(auth.updateAccount).toHaveBeenCalledWith({
        username: 'admin',
        email: 'admin@example.com',
        changePassword: false,
        currentPassword: 'current1',
      });
      expect(usersService.update).not.toHaveBeenCalled();
      expect(modal.close).toHaveBeenCalledWith({ user: ADMIN, passwordChanged: false });
    });
  });
});
