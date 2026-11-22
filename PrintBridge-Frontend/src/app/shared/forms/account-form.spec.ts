import { FormBuilder, FormControl } from '@angular/forms';
import {
  maxUtf8Bytes,
  newAccountForm,
  toAccountUpdate,
  toUserUpdate,
  updateAccountForm,
} from './account-form';
import { fieldError } from './field-error';

describe('account form', () => {
  it('flags mismatching passwords', () => {
    const form = newAccountForm(new FormBuilder());
    form.setValue({
      username: 'bob',
      email: 'bob@example.com',
      password: 'password1',
      confirmPassword: 'password2',
    });
    expect(form.hasError('passwordMismatch')).toBe(true);
    form.controls.confirmPassword.setValue('password1');
    expect(form.valid).toBe(true);
  });

  it('limits passwords to the 72 bytes bcrypt hashes', () => {
    const form = newAccountForm(new FormBuilder());
    const password = form.controls.password;

    password.setValue('a'.repeat(72));
    expect(password.valid).toBe(true);
    password.setValue('a'.repeat(73));
    expect(password.errors).toEqual({ maxbytes: { max: 72, actual: 73 } });

    // An emoji takes four bytes: 18 of them fit exactly, one more letter doesn't.
    password.setValue('😀'.repeat(18));
    expect(password.valid).toBe(true);
    password.setValue('😀'.repeat(18) + 'a');
    expect(password.hasError('maxbytes')).toBe(true);
    password.markAsTouched();
    expect(fieldError(password, 'Password')).toBe('Password is too long');
  });

  it('leaves values that are not text to other validators', () => {
    expect(maxUtf8Bytes(1)(new FormControl(null))).toBeNull();
  });

  describe('update form', () => {
    const bob = { username: 'bob', email: 'bob@example.com' };

    it('starts with the details filled in and the passwords empty', () => {
      const form = updateAccountForm(new FormBuilder(), bob, false);
      expect(form.getRawValue()).toEqual({
        username: 'bob',
        email: 'bob@example.com',
        password: '',
        confirmPassword: '',
        currentPassword: '',
      });
      expect(form.valid).toBe(true);
    });

    it('checks a new password only when one is typed', () => {
      const form = updateAccountForm(new FormBuilder(), bob, false);
      form.controls.password.setValue('short');
      expect(form.controls.password.hasError('minlength')).toBe(true);
      form.controls.password.setValue('password1');
      expect(form.hasError('passwordMismatch')).toBe(true);
      form.controls.confirmPassword.setValue('password1');
      expect(form.valid).toBe(true);
    });

    it('requires the current password only for your own account', () => {
      expect(updateAccountForm(new FormBuilder(), bob, true).valid).toBe(false);
      const form = updateAccountForm(new FormBuilder(), bob, true);
      form.controls.currentPassword.setValue('current1');
      expect(form.valid).toBe(true);
    });

    it('only asks to change the password when one is typed', () => {
      const form = updateAccountForm(new FormBuilder(), bob, true);
      form.controls.currentPassword.setValue('current1');
      expect(toUserUpdate(form)).toEqual({
        username: 'bob',
        email: 'bob@example.com',
        changePassword: false,
      });

      form.controls.password.setValue('password1');
      expect(toAccountUpdate(form)).toEqual({
        username: 'bob',
        email: 'bob@example.com',
        changePassword: true,
        newPassword: 'password1',
        currentPassword: 'current1',
      });
    });
  });
});
