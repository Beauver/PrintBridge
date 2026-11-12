import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { User } from '../../core/models/account.model';
import { ADMIN, ALICE } from '../../core/testing';
import { UsersPage } from './users-page';
import { UsersService } from './users.service';

describe('UsersPage', () => {
  let page: UsersPage;
  const usersService = {
    getAll: vi.fn(),
    create: vi.fn(),
    resetPassword: vi.fn(),
    delete: vi.fn(),
  };
  const httpError = (status: number, message?: string) =>
    throwError(() => new HttpErrorResponse({ status, error: message ? { message } : null }));
  const BOB: User = { ...ALICE, id: 3, username: 'bob', email: 'bob@example.com' };

  beforeEach(() => {
    vi.clearAllMocks();
    usersService.getAll.mockReturnValue(of([ADMIN, ALICE]));
    TestBed.configureTestingModule({
      providers: [
        { provide: UsersService, useValue: usersService },
        { provide: AuthService, useValue: { user: signal(ADMIN) } },
      ],
    });
    page = TestBed.createComponent(UsersPage).componentInstance;
    page.ngOnInit();
  });

  describe('loading', () => {
    it('loads the users', () => {
      expect(page['users']()).toEqual([ADMIN, ALICE]);
      expect(page['loading']()).toBe(false);
    });

    it('shows a load error', () => {
      usersService.getAll.mockReturnValue(httpError(500));
      page['load']();
      expect(page['error']()).toBe('Could not load users.');
      expect(page['loading']()).toBe(false);
    });

    it('knows which user is you', () => {
      expect(page['isSelf'](ADMIN)).toBe(true);
      expect(page['isSelf'](ALICE)).toBe(false);
    });
  });

  describe('creating', () => {
    beforeEach(() => {
      page['toggleCreate']();
      page['createForm'].setValue({
        username: 'bob',
        email: 'bob@example.com',
        password: 'password123',
        confirmPassword: 'password123',
      });
    });

    it('creates a user and adds it to the sorted list', () => {
      usersService.create.mockReturnValue(of(BOB));
      page['create']();

      expect(usersService.create).toHaveBeenCalledWith({
        username: 'bob',
        email: 'bob@example.com',
        password: 'password123',
      });
      expect(page['users']().map((u) => u.username)).toEqual(['admin', 'alice', 'bob']);
      expect(page['showCreate']()).toBe(false);
      expect(page['notice']()).toContain('"bob" created');
    });

    it('does not submit an invalid form', () => {
      page['createForm'].controls.confirmPassword.setValue('mismatch1');
      page['create']();
      expect(usersService.create).not.toHaveBeenCalled();
    });

    it('shows the server error', () => {
      usersService.create.mockReturnValue(httpError(409, 'Username is already taken'));
      page['create']();
      expect(page['createError']()).toBe('Username is already taken');
      expect(page['creating']()).toBe(false);
    });

    it('resets the form when toggled', () => {
      page['toggleCreate']();
      expect(page['showCreate']()).toBe(false);
      expect(page['createForm'].controls.username.value).toBe('');
    });
  });

  describe('resetting a password', () => {
    beforeEach(() => page['startReset'](ALICE));

    it('requires a valid password', () => {
      page['saveReset'](ALICE);
      expect(usersService.resetPassword).not.toHaveBeenCalled();
    });

    it('resets the password', () => {
      usersService.resetPassword.mockReturnValue(of(undefined));
      page['resetPassword'].setValue('new-password');
      page['saveReset'](ALICE);

      expect(usersService.resetPassword).toHaveBeenCalledWith(2, 'new-password');
      expect(page['resettingId']()).toBeNull();
      expect(page['notice']()).toContain('logged out everywhere');
    });

    it('shows an error when it fails', () => {
      usersService.resetPassword.mockReturnValue(httpError(404, 'User not found'));
      page['resetPassword'].setValue('new-password');
      page['saveReset'](ALICE);
      expect(page['error']()).toBe('User not found');
      expect(page['busyId']()).toBeNull();
    });

    it('can be cancelled', () => {
      page['cancelReset']();
      expect(page['resettingId']()).toBeNull();
    });
  });

  describe('deleting', () => {
    it('asks for confirmation first, closing an open reset', () => {
      page['startReset'](ALICE);
      page['askDelete'](ALICE);
      expect(page['confirmingDeleteId']()).toBe(2);
      expect(page['resettingId']()).toBeNull();
      expect(usersService.delete).not.toHaveBeenCalled();
    });

    it('deletes after confirmation', () => {
      usersService.delete.mockReturnValue(of(undefined));
      page['askDelete'](ALICE);
      page['confirmDelete'](ALICE);

      expect(usersService.delete).toHaveBeenCalledWith(2);
      expect(page['users']()).toEqual([ADMIN]);
      expect(page['confirmingDeleteId']()).toBeNull();
    });

    it('shows an error when it fails', () => {
      usersService.delete.mockReturnValue(httpError(409, 'You cannot delete your own account'));
      page['confirmDelete'](ADMIN);
      expect(page['error']()).toBe('You cannot delete your own account');
    });

    it('can be cancelled', () => {
      page['askDelete'](ALICE);
      page['cancelDelete']();
      expect(page['confirmingDeleteId']()).toBeNull();
    });
  });
});
