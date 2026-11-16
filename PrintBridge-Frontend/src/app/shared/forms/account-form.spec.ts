import { FormBuilder, FormControl } from '@angular/forms';
import { maxUtf8Bytes, newAccountForm } from './account-form';
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
});
