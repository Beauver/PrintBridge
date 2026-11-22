import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../core/http/api-error';
import { User } from '../../core/models/account.model';
import { newAccountForm } from '../../shared/forms/account-form';
import { fieldError } from '../../shared/forms/field-error';
import { UpdateUserDialog, UserUpdated } from './update-user-dialog/update-user-dialog';
import { UsersService } from './users.service';

/** Puts the updated user in place of the old one, keeping the list sorted by username. */
function replaceUser(users: User[], updated: User): User[] {
  const others = users.filter((user) => {
    return user.id !== updated.id;
  });
  return [...others, updated].sort(compareByUsername);
}

function compareByUsername(firstUser: User, secondUser: User): number {
  return firstUser.username.localeCompare(secondUser.username);
}

function updatedNotice(result: UserUpdated, ownAccount: boolean): string {
  if (ownAccount) {
    if (result.passwordChanged) {
      return 'Your account has been updated. You have been logged out on other devices.';
    }
    return 'Your account has been updated.';
  }
  if (result.passwordChanged) {
    return `Account "${result.user.username}" updated. They have been logged out everywhere.`;
  }
  return `Account "${result.user.username}" updated.`;
}

/** Admin page: list, create, update and delete accounts. */
@Component({
  selector: 'app-users-page',
  imports: [ReactiveFormsModule, DatePipe, NgTemplateOutlet],
  templateUrl: './users-page.html',
})
export class UsersPage implements OnInit {
  private readonly usersService = inject(UsersService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly modalService = inject(NgbModal);

  protected readonly users = signal<User[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly showCreate = signal(false);
  protected readonly createForm = newAccountForm(this.fb);
  protected readonly creating = signal(false);
  protected readonly createError = signal<string | null>(null);

  /** Id of the user whose deletion awaits confirmation. */
  protected readonly confirmingDeleteId = signal<number | null>(null);
  protected readonly busyId = signal<number | null>(null);

  protected readonly fieldError = fieldError;

  ngOnInit(): void {
    this.load();
  }

  protected isSelf(user: User): boolean {
    return user.id === this.auth.user()?.id;
  }

  protected load(): void {
    this.loading.set(true);
    this.usersService.getAll().subscribe({
      next: (users) => {
        this.users.set(users);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(errorMessage(err, 'Could not load users.'));
        this.loading.set(false);
      },
    });
  }

  protected toggleCreate(): void {
    this.showCreate.update((show) => !show);
    this.createForm.reset();
    this.createError.set(null);
  }

  protected create(): void {
    this.createForm.markAllAsTouched();
    if (this.createForm.invalid) {
      return;
    }
    const { username, email, password } = this.createForm.getRawValue();
    this.creating.set(true);
    this.createError.set(null);
    this.usersService.create({ username, email, password }).subscribe({
      next: (user) => {
        this.creating.set(false);
        this.showCreate.set(false);
        this.createForm.reset();
        this.users.update((users) => [...users, user].sort(compareByUsername));
        this.notice.set(`Account "${user.username}" created.`);
      },
      error: (err: unknown) => {
        this.creating.set(false);
        this.createError.set(errorMessage(err));
      },
    });
  }

  protected openUpdate(user: User): void {
    this.confirmingDeleteId.set(null);
    const modal = this.modalService.open(UpdateUserDialog, { size: 'lg' });
    const dialog = modal.componentInstance as UpdateUserDialog;
    dialog.setUser(user);
    modal.result.then(
      (result: UserUpdated) => {
        this.showUpdated(result);
      },
      () => {
        // Cancelled: nothing changes.
      },
    );
  }

  private showUpdated(result: UserUpdated): void {
    this.users.update((users) => replaceUser(users, result.user));
    this.notice.set(updatedNotice(result, this.isSelf(result.user)));
  }

  protected askDelete(user: User): void {
    this.confirmingDeleteId.set(user.id);
  }

  protected cancelDelete(): void {
    this.confirmingDeleteId.set(null);
  }

  protected confirmDelete(user: User): void {
    this.busyId.set(user.id);
    this.usersService.delete(user.id).subscribe({
      next: () => {
        this.busyId.set(null);
        this.confirmingDeleteId.set(null);
        this.users.update((users) => users.filter((u) => u.id !== user.id));
        this.notice.set(`Account "${user.username}" deleted.`);
      },
      error: (err: unknown) => {
        this.busyId.set(null);
        this.error.set(errorMessage(err));
      },
    });
  }
}
