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

/** User-facing text for the first error on a control, or null. */
export function fieldError(control: AbstractControl | null, label: string): string | null {
  if (!control || !control.errors || !(control.touched || control.dirty)) {
    return null;
  }
  const errors = control.errors;
  if (errors['required']) return `${label} is required`;
  if (errors['email']) return 'Email is not valid';
  if (errors['minlength'])
    return `${label} must be at least ${errors['minlength'].requiredLength} characters`;
  if (errors['maxlength'])
    return `${label} must be at most ${errors['maxlength'].requiredLength} characters`;
  if (errors['pattern']) return `${label} may only contain letters, digits, '.', '_' and '-'`;
  return `${label} is not valid`;
}
