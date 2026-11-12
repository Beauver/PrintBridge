import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../core/http/api-error';
import { SetupStatusService } from '../../core/setup/setup-status.service';
import { fieldError, newAccountForm } from '../../shared/account-form';

/** First-run page: creates the admin account. Only reachable while no admin exists. */
@Component({
  selector: 'app-setup-page',
  imports: [ReactiveFormsModule],
  templateUrl: './setup-page.html',
})
export class SetupPage {
  private readonly auth = inject(AuthService);
  private readonly setupStatus = inject(SetupStatusService);
  private readonly router = inject(Router);

  protected readonly form = newAccountForm(inject(FormBuilder));
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly fieldError = fieldError;

  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }
    const { username, email, password } = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    this.auth.setup({ username, email, password }).subscribe({
      next: () => {
        this.setupStatus.markDone();
        void this.router.navigate(['/']);
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.error.set(errorMessage(err));
      },
    });
  }
}
