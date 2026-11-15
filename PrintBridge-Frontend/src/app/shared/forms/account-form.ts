import {
  AbstractControl,
  FormBuilder,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';

/** Same rules as the backend's NewAccountRequest. */
export const USERNAME_VALIDATORS = [
  Validators.required,
  Validators.minLength(3),
  Validators.maxLength(32),
  Validators.pattern(/^[A-Za-z0-9_.-]+$/),
];
export const EMAIL_VALIDATORS = [Validators.required, Validators.email, Validators.maxLength(254)];
export const PASSWORD_VALIDATORS = [
  Validators.required,
  Validators.minLength(8),
  Validators.maxLength(72),
];

/** Group validator: `password` and `confirmPassword` must be equal. */
export const passwordsMatch: ValidatorFn = (group: AbstractControl): ValidationErrors | null =>
  group.get('password')?.value === group.get('confirmPassword')?.value
    ? null
    : { passwordMismatch: true };

/** Form for a new account with password confirmation (setup and admin "new user"). */
export function newAccountForm(fb: FormBuilder) {
  return fb.nonNullable.group(
    {
      username: ['', USERNAME_VALIDATORS],
      email: ['', EMAIL_VALIDATORS],
      password: ['', PASSWORD_VALIDATORS],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordsMatch },
  );
}
