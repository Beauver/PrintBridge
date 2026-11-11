import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../core/http/api-error';
import { User } from '../../core/models';
import { fieldError, newAccountForm, PASSWORD_VALIDATORS } from '../../shared/account-form';
import { UsersService } from './users.service';

/** Admin page: list, create, reset password and delete accounts. */
@Component({
  selector: 'app-users-page',
  imports: [ReactiveFormsModule, DatePipe, NgTemplateOutlet],
  templateUrl: './users-page.html',
})
export class UsersPage implements OnInit {
  private readonly usersService = inject(UsersService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly users = signal<User[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly showCreate = signal(false);
  protected readonly createForm = newAccountForm(this.fb);
  protected readonly creating = signal(false);
  protected readonly createError = signal<string | null>(null);

  /** Id of the user whose password is being reset / whose deletion awaits confirmation. */
  protected readonly resettingId = signal<number | null>(null);
  protected readonly resetPassword = this.fb.nonNullable.control('', PASSWORD_VALIDATORS);
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
    this.usersService.list().subscribe({
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
        this.users.update((users) =>
          [...users, user].sort((a, b) => a.username.localeCompare(b.username)),
        );
        this.notice.set(`Account "${user.username}" created.`);
      },
      error: (err: unknown) => {
        this.creating.set(false);
        this.createError.set(errorMessage(err));
      },
    });
  }

  protected startReset(user: User): void {
    this.confirmingDeleteId.set(null);
    this.resetPassword.reset();
    this.resettingId.set(user.id);
  }

  protected cancelReset(): void {
    this.resettingId.set(null);
  }

  protected saveReset(user: User): void {
    this.resetPassword.markAsTouched();
    if (this.resetPassword.invalid) {
      return;
    }
    this.busyId.set(user.id);
    this.usersService.resetPassword(user.id, this.resetPassword.value).subscribe({
      next: () => {
        this.busyId.set(null);
        this.resettingId.set(null);
        this.notice.set(
          `Password of "${user.username}" reset. They have been logged out everywhere.`,
        );
      },
      error: (err: unknown) => {
        this.busyId.set(null);
        this.error.set(errorMessage(err));
      },
    });
  }

  protected askDelete(user: User): void {
    this.resettingId.set(null);
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
