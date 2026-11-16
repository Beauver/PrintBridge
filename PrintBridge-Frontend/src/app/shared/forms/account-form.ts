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
/** bcrypt hashes only the first 72 bytes of a password; anything after that would not count. */
const MAX_PASSWORD_BYTES = 72;

/**
 * At most `max` bytes in UTF-8, the same rule as the backend's `@MaxUtf8Bytes`. Accented letters
 * take two bytes and emoji four, so this can be fewer characters than `max`.
 */
export function maxUtf8Bytes(max: number): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value;
    if (typeof value !== 'string') {
      return null;
    }
    const actual = new TextEncoder().encode(value).length;
    if (actual <= max) {
      return null;
    }
    return { maxbytes: { max, actual } };
  };
}

export const PASSWORD_VALIDATORS = [
  Validators.required,
  Validators.minLength(8),
  maxUtf8Bytes(MAX_PASSWORD_BYTES),
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
