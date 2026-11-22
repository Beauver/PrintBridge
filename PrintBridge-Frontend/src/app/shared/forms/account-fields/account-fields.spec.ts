import { FormBuilder } from '@angular/forms';
import { TestBed } from '@angular/core/testing';
import { updateAccountForm } from '../account-form';
import { AccountFields } from './account-fields';

describe('AccountFields', () => {
  it('asks for the current password only when told to', () => {
    const fixture = TestBed.createComponent(AccountFields);
    const form = updateAccountForm(
      new FormBuilder(),
      { username: 'bob', email: 'b@example.com' },
      true,
    );
    fixture.componentRef.setInput('form', form);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[data-cy="account-current-password"]')).toBeNull();

    fixture.componentRef.setInput('askCurrentPassword', true);
    fixture.detectChanges();
    expect(element.querySelector('[data-cy="account-current-password"]')).not.toBeNull();
  });
});
