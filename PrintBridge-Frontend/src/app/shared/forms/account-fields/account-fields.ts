import { Component, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { UpdateAccountForm } from '../account-form';
import { fieldError } from '../field-error';

/**
 * The fields for updating an account (#28), shared by the admin's "Update user" dialog and the
 * My account page. The parent owns the form and submits it.
 */
@Component({
  selector: 'app-account-fields',
  imports: [ReactiveFormsModule],
  templateUrl: './account-fields.html',
})
export class AccountFields {
  readonly form = input.required<UpdateAccountForm>();
  /** Only when the user updates their own account. */
  readonly askCurrentPassword = input(false);
  /** Keeps the input ids unique when the fields appear more than once on a page. */
  readonly idPrefix = input('account');

  protected readonly fieldError = fieldError;
}
