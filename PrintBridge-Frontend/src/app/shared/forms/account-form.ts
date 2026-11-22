import {
  AbstractControl,
  FormBuilder,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { AccountUpdate, UserUpdate } from '../../core/models/account.model';

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
/** For a password that may stay empty (keep the current one), but has the usual rules otherwise. */
const OPTIONAL_PASSWORD_VALIDATORS = [Validators.minLength(8), maxUtf8Bytes(MAX_PASSWORD_BYTES)];

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

/**
 * Form for updating an existing account: username and email filled in, password fields empty
 * (empty keeps the current password). `currentPassword` is required when the user updates
 * their own account.
 */
export function updateAccountForm(
  fb: FormBuilder,
  user: { username: string; email: string },
  ownAccount: boolean,
) {
  const currentPasswordValidators = [];
  if (ownAccount) {
    currentPasswordValidators.push(Validators.required);
  }
  return fb.nonNullable.group(
    {
      username: [user.username, USERNAME_VALIDATORS],
      email: [user.email, EMAIL_VALIDATORS],
      password: ['', OPTIONAL_PASSWORD_VALIDATORS],
      confirmPassword: [''],
      currentPassword: ['', currentPasswordValidators],
    },
    { validators: passwordsMatch },
  );
}

export type UpdateAccountForm = ReturnType<typeof updateAccountForm>;

/** The request body for an admin update; the password only changes when one was typed. */
export function toUserUpdate(form: UpdateAccountForm): UserUpdate {
  const { username, email, password } = form.getRawValue();
  if (password === '') {
    return { username, email, changePassword: false };
  }
  return { username, email, changePassword: true, newPassword: password };
}

/** The request body for updating your own account. */
export function toAccountUpdate(form: UpdateAccountForm): AccountUpdate {
  return { ...toUserUpdate(form), currentPassword: form.controls.currentPassword.value };
}
