import { FormBuilder, FormControl, Validators } from '@angular/forms';
import { newAccountForm } from './account-form';
import { fieldError } from './field-error';

describe('field error', () => {
  it('describes field errors once touched', () => {
    const control = new FormControl('', [Validators.required]);
    expect(fieldError(control, 'Name')).toBeNull();
    control.markAsTouched();
    expect(fieldError(control, 'Name')).toBe('Name is required');
    expect(fieldError(null, 'Name')).toBeNull();
  });

  it('describes each validator', () => {
    const form = newAccountForm(new FormBuilder());
    form.markAllAsTouched();
    form.controls.username.setValue('a b');
    expect(fieldError(form.controls.username, 'Username')).toContain('only contain');
    form.controls.username.setValue('ab');
    expect(fieldError(form.controls.username, 'Username')).toBe(
      'Username must be at least 3 characters',
    );
    form.controls.username.setValue('x'.repeat(33));
    expect(fieldError(form.controls.username, 'Username')).toBe(
      'Username must be at most 32 characters',
    );
    form.controls.email.setValue('nope');
    expect(fieldError(form.controls.email, 'Email')).toBe('Email is not valid');

    const custom = new FormControl('x', () => ({ other: true }));
    custom.markAsTouched();
    expect(fieldError(custom, 'Field')).toBe('Field is not valid');
  });
});
