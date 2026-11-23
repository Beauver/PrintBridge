import { Component, inject, input, OnInit, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { errorMessage } from '../../../../core/http/api-error';
import { PrintRequest } from '../../../../core/models/print-request.model';
import { PrintRequestsService } from '../../print-requests.service';

/** Same limits as the backend (and as a new request). */
export const MAX_NAME_LENGTH = 100;
export const MAX_DESCRIPTION_LENGTH = 2000;

/**
 * Edits a request's name and description in place (#84). The form is filled once when editing
 * starts, so the page reloading the request (while files are sliced) doesn't wipe typed text.
 */
@Component({
  selector: 'app-details-editor',
  imports: [ReactiveFormsModule],
  templateUrl: './details-editor.html',
})
export class DetailsEditor implements OnInit {
  private readonly requestsService = inject(PrintRequestsService);
  private readonly fb = inject(FormBuilder);

  readonly request = input.required<PrintRequest>();
  /** The request as saved. */
  readonly saved = output<PrintRequest>();
  readonly cancelled = output<void>();

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(MAX_NAME_LENGTH)]],
    description: ['', [Validators.maxLength(MAX_DESCRIPTION_LENGTH)]],
  });
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    const request = this.request();
    this.form.setValue({ name: request.name, description: request.description ?? '' });
  }

  protected save(): void {
    this.error.set(null);
    const value = this.form.getRawValue();
    const problem = validationProblem(value.name, value.description);
    if (problem !== null) {
      this.error.set(problem);
      return;
    }
    this.saving.set(true);
    this.requestsService
      .changeDetails(this.request().id, value.name.trim(), value.description)
      .subscribe({
        next: (updated) => {
          this.saving.set(false);
          this.saved.emit(updated);
        },
        error: (err: unknown) => {
          this.saving.set(false);
          this.error.set(errorMessage(err, 'Could not save the name and description.'));
        },
      });
  }

  protected cancel(): void {
    this.cancelled.emit();
  }
}

/** What is wrong with the typed values, in the backend's words; null when they are fine. */
function validationProblem(name: string, description: string): string | null {
  if (name.trim() === '') {
    return 'Name is required';
  }
  if (name.length > MAX_NAME_LENGTH) {
    return `Name must be at most ${MAX_NAME_LENGTH} characters`;
  }
  if (description.length > MAX_DESCRIPTION_LENGTH) {
    return `Description must be at most ${MAX_DESCRIPTION_LENGTH} characters`;
  }
  return null;
}
