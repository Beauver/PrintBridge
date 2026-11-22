import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { errorMessage } from '../../../core/http/api-error';
import { User } from '../../../core/models/account.model';
import {
  toAccountUpdate,
  toUserUpdate,
  UpdateAccountForm,
  updateAccountForm,
} from '../../../shared/forms/account-form';
import { AccountFields } from '../../../shared/forms/account-fields/account-fields';
import { UsersService } from '../users.service';

/** What the dialog closes with. */
export interface UserUpdated {
  user: User;
  passwordChanged: boolean;
}

/**
 * "Update user" (#28): the admin edits a user's username, email and password. On the admin's own
 * row it works like the My account page: the current password is required and this browser stays
 * logged in. Opened with NgbModal by the users page, which calls `setUser` right after opening;
 * closes with a `UserUpdated`, or is dismissed.
 */
@Component({
  selector: 'app-update-user-dialog',
  imports: [ReactiveFormsModule, AccountFields],
  templateUrl: './update-user-dialog.html',
})
export class UpdateUserDialog {
  private readonly modal = inject(NgbActiveModal);
  private readonly usersService = inject(UsersService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly user = signal<User | null>(null);
  protected readonly ownAccount = signal(false);
  protected readonly form = signal<UpdateAccountForm>(
    updateAccountForm(this.fb, { username: '', email: '' }, false),
  );
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);

  setUser(user: User): void {
    const ownAccount = user.id === this.auth.user()?.id;
    this.user.set(user);
    this.ownAccount.set(ownAccount);
    this.form.set(updateAccountForm(this.fb, user, ownAccount));
  }

  protected save(): void {
    const user = this.user();
    const form = this.form();
    form.markAllAsTouched();
    if (!user || form.invalid) {
      return;
    }
    const passwordChanged = form.controls.password.value !== '';
    this.saving.set(true);
    this.saveError.set(null);
    this.saveRequest(user, form).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.modal.close({ user: updated, passwordChanged } satisfies UserUpdated);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.saveError.set(errorMessage(err));
      },
    });
  }

  protected cancel(): void {
    this.modal.dismiss();
  }

  private saveRequest(user: User, form: UpdateAccountForm): Observable<User> {
    if (this.ownAccount()) {
      return this.auth.updateAccount(toAccountUpdate(form));
    }
    return this.usersService.update(user.id, toUserUpdate(form));
  }
}
