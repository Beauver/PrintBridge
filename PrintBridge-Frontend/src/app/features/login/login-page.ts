import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../core/http/api-error';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule],
  templateUrl: './login-page.html',
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    login: ['', Validators.required],
    password: ['', Validators.required],
  });
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }
    const { login, password } = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    this.auth.login(login, password).subscribe({
      next: () => void this.router.navigate(['/']),
      error: (err: unknown) => {
        this.busy.set(false);
        this.error.set(errorMessage(err));
        this.form.controls.password.reset();
      },
    });
  }
}
