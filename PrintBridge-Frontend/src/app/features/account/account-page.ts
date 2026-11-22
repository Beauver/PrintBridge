import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../core/http/api-error';
import {
  toAccountUpdate,
  UpdateAccountForm,
  updateAccountForm,
} from '../../shared/forms/account-form';
import { AccountFields } from '../../shared/forms/account-fields/account-fields';

/** My account (#28): every user updates their own username, email and password. */
@Component({
  selector: 'app-account-page',
  imports: [ReactiveFormsModule, AccountFields],
  templateUrl: './account-page.html',
})
export class AccountPage {
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly form = signal<UpdateAccountForm>(this.createForm());
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected save(): void {
    const form = this.form();
    form.markAllAsTouched();
    if (form.invalid) {
      return;
    }
    const passwordChanged = form.controls.password.value !== '';
    this.saving.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.auth.updateAccount(toAccountUpdate(form)).subscribe({
      next: () => {
        this.saving.set(false);
        // Filled in again from the updated user, with the password fields empty.
        this.form.set(this.createForm());
        this.notice.set(savedNotice(passwordChanged));
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(errorMessage(err));
      },
    });
  }

  private createForm(): UpdateAccountForm {
    const user = this.auth.user();
    let details = { username: '', email: '' };
    if (user) {
      details = { username: user.username, email: user.email };
    }
    return updateAccountForm(this.fb, details, true);
  }
}

function savedNotice(passwordChanged: boolean): string {
  if (passwordChanged) {
    return 'Your account has been updated. You have been logged out on other devices.';
  }
  return 'Your account has been updated.';
}
