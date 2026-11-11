import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { User } from '../../core/models';
import { ADMIN, ALICE } from '../../core/testing';
import { byCy, click, submit, type } from '../../core/testing-dom';
import { UsersPage } from './users-page';
import { UsersService } from './users.service';

describe('UsersPage', () => {
  let fixture: ComponentFixture<UsersPage>;
  const usersService = { list: vi.fn(), create: vi.fn(), resetPassword: vi.fn(), delete: vi.fn() };
  const conflict = (message: string) =>
    throwError(() => new HttpErrorResponse({ status: 409, error: { message } }));

  async function render(list = of<User[]>([ADMIN, ALICE])) {
    usersService.list.mockReturnValue(list);
    TestBed.configureTestingModule({
      imports: [UsersPage],
      providers: [
        { provide: UsersService, useValue: usersService },
        { provide: AuthService, useValue: { user: signal(ADMIN) } },
      ],
    });
    fixture = TestBed.createComponent(UsersPage);
    await fixture.whenStable();
  }

  const row = (username: string) => byCy(fixture, `user-row-${username}`)!;

  beforeEach(() => vi.clearAllMocks());

  it('lists users', async () => {
    await render();
    expect(row('admin').textContent).toContain('(you)');
    expect(row('alice').textContent).toContain('alice@example.com');
  });

  it('shows a load error', async () => {
    await render(throwError(() => new HttpErrorResponse({ status: 500 })));
    expect(byCy(fixture, 'users-error')!.textContent).toContain('Could not load users.');
  });

  it('does not offer deleting your own account', async () => {
    await render();
    expect(row('admin').querySelector('[data-cy="user-delete"]')).toBeNull();
    expect(row('alice').querySelector('[data-cy="user-delete"]')).not.toBeNull();
  });

  async function openCreateAndFill(confirm = 'password123') {
    click(fixture, 'users-new');
    await fixture.whenStable();
    type(fixture, 'users-create-username', 'bob');
    type(fixture, 'users-create-email', 'bob@example.com');
    type(fixture, 'users-create-password', 'password123');
    type(fixture, 'users-create-confirm-password', confirm);
  }

  it('creates a user', async () => {
    await render();
    const bob: User = { ...ALICE, id: 3, username: 'bob', email: 'bob@example.com' };
    usersService.create.mockReturnValue(of(bob));
    await openCreateAndFill();
    submit(fixture, 'users-create-form');
    await fixture.whenStable();

    expect(usersService.create).toHaveBeenCalledWith({
      username: 'bob',
      email: 'bob@example.com',
      password: 'password123',
    });
    expect(row('bob')).not.toBeNull();
    expect(byCy(fixture, 'users-notice')!.textContent).toContain('"bob" created');
    expect(byCy(fixture, 'users-create-form')).toBeNull();
  });

  it('does not submit an invalid new user', async () => {
    await render();
    await openCreateAndFill('mismatch1');
    submit(fixture, 'users-create-form');
    await fixture.whenStable();
    expect(usersService.create).not.toHaveBeenCalled();
  });

  it('shows a duplicate error from the server', async () => {
    await render();
    usersService.create.mockReturnValue(conflict('Username is already taken'));
    await openCreateAndFill();
    submit(fixture, 'users-create-form');
    await fixture.whenStable();
    expect(byCy(fixture, 'users-create-error')!.textContent).toContain('Username is already taken');
  });

  it('resets a password', async () => {
    await render();
    usersService.resetPassword.mockReturnValue(of(undefined));
    (row('alice').querySelector('[data-cy="user-reset"]') as HTMLElement).click();
    await fixture.whenStable();

    click(fixture, 'user-reset-save');
    await fixture.whenStable();
    expect(usersService.resetPassword).not.toHaveBeenCalled(); // empty password

    type(fixture, 'user-reset-password', 'new-password');
    click(fixture, 'user-reset-save');
    await fixture.whenStable();
    expect(usersService.resetPassword).toHaveBeenCalledWith(2, 'new-password');
    expect(byCy(fixture, 'user-reset-form')).toBeNull();
    expect(byCy(fixture, 'users-notice')!.textContent).toContain('logged out everywhere');
  });

  it('shows an error when the reset fails', async () => {
    await render();
    usersService.resetPassword.mockReturnValue(
      throwError(
        () => new HttpErrorResponse({ status: 404, error: { message: 'User not found' } }),
      ),
    );
    (row('alice').querySelector('[data-cy="user-reset"]') as HTMLElement).click();
    await fixture.whenStable();
    type(fixture, 'user-reset-password', 'new-password');
    click(fixture, 'user-reset-save');
    await fixture.whenStable();
    expect(byCy(fixture, 'users-error')!.textContent).toContain('User not found');
  });

  it('deletes a user after confirmation', async () => {
    await render();
    usersService.delete.mockReturnValue(of(undefined));
    (row('alice').querySelector('[data-cy="user-delete"]') as HTMLElement).click();
    await fixture.whenStable();
    expect(byCy(fixture, 'user-delete-confirm')!.textContent).toContain(
      'requests will be deleted too',
    );
    expect(usersService.delete).not.toHaveBeenCalled();

    click(fixture, 'user-delete-yes');
    await fixture.whenStable();
    expect(usersService.delete).toHaveBeenCalledWith(2);
    expect(byCy(fixture, 'user-row-alice')).toBeNull();
  });

  it('shows an error when deleting fails', async () => {
    await render();
    usersService.delete.mockReturnValue(conflict('You cannot delete your own account'));
    (row('alice').querySelector('[data-cy="user-delete"]') as HTMLElement).click();
    await fixture.whenStable();
    click(fixture, 'user-delete-yes');
    await fixture.whenStable();
    expect(byCy(fixture, 'users-error')!.textContent).toContain(
      'You cannot delete your own account',
    );
  });
});
