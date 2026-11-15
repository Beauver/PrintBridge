import { FormBuilder } from '@angular/forms';
import { newAccountForm } from './account-form';

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
});
