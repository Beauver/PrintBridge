import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { User } from '../../core/models/account.model';
import { ADMIN, ALICE } from '../../../testing/fixtures';
import { UpdateUserDialog } from './update-user-dialog/update-user-dialog';
import { UsersPage } from './users-page';
import { UsersService } from './users.service';

describe('UsersPage', () => {
  let page: UsersPage;
  let modalService: NgbModal;
  const usersService = {
    getAll: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  };
  const httpError = (status: number, message?: string) =>
    throwError(() => new HttpErrorResponse({ status, error: message ? { message } : null }));
  function getUsername(user: User): string {
    return user.username;
  }
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
    modalService = TestBed.inject(NgbModal);
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

  describe('updating', () => {
    const dialog = { setUser: vi.fn() };

    function openWith(result: Promise<unknown>): void {
      vi.spyOn(modalService, 'open').mockReturnValue({
        componentInstance: dialog,
        result,
      } as unknown as NgbModalRef);
    }

    it('opens the dialog for the user, closing an open delete confirmation', () => {
      openWith(
        new Promise(() => {
          // Stays open.
        }),
      );
      page['askDelete'](ALICE);
      page['openUpdate'](ALICE);

      expect(modalService.open).toHaveBeenCalledWith(UpdateUserDialog, { size: 'lg' });
      expect(dialog.setUser).toHaveBeenCalledWith(ALICE);
      expect(page['confirmingDeleteId']()).toBeNull();
    });

    it('shows the updated user in the sorted list', async () => {
      const renamed: User = { ...ALICE, username: 'zoe' };
      openWith(Promise.resolve({ user: renamed, passwordChanged: false }));
      page['openUpdate'](ALICE);
      await Promise.resolve();

      expect(page['users']().map(getUsername)).toEqual(['admin', 'zoe']);
      expect(page['notice']()).toBe('Account "zoe" updated.');
    });

    it('says the user was logged out after a new password', async () => {
      openWith(Promise.resolve({ user: ALICE, passwordChanged: true }));
      page['openUpdate'](ALICE);
      await Promise.resolve();
      expect(page['notice']()).toBe(
        'Account "alice" updated. They have been logged out everywhere.',
      );
    });

    it('talks about your own account on your own row', async () => {
      openWith(Promise.resolve({ user: ADMIN, passwordChanged: false }));
      page['openUpdate'](ADMIN);
      await Promise.resolve();
      expect(page['notice']()).toBe('Your account has been updated.');

      openWith(Promise.resolve({ user: ADMIN, passwordChanged: true }));
      page['openUpdate'](ADMIN);
      await Promise.resolve();
      expect(page['notice']()).toBe(
        'Your account has been updated. You have been logged out on other devices.',
      );
    });

    it('changes nothing when cancelled', async () => {
      openWith(Promise.reject(new Error('dismissed')));
      page['openUpdate'](ALICE);
      await Promise.resolve();
      await Promise.resolve();
      expect(page['users']()).toEqual([ADMIN, ALICE]);
      expect(page['notice']()).toBeNull();
    });
  });

  describe('deleting', () => {
    it('asks for confirmation first', () => {
      page['askDelete'](ALICE);
      expect(page['confirmingDeleteId']()).toBe(2);
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
