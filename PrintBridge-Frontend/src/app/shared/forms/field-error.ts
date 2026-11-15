import { AbstractControl } from '@angular/forms';

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
