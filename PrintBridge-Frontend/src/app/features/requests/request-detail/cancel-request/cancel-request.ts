import { Component, computed, inject, input, output, signal } from '@angular/core';
import { errorMessage } from '../../../../core/http/api-error';
import { PrintRequest } from '../../../../core/models/print-request.model';
import { allowsCancelling } from '../../../../shared/format/print-request-display';
import { PrintRequestsService } from '../../print-requests.service';

/** Same limit as the backend. */
export const MAX_CANCEL_REASON = 2000;

/**
 * The requester cancels their request before the admin accepted it (#85), after confirming with
 * an optional reason. The admin declines instead, so it never shows for them. Hands the
 * cancelled request to the page.
 */
@Component({
  selector: 'app-cancel-request',
  templateUrl: './cancel-request.html',
  styleUrl: './cancel-request.css',
})
export class CancelRequestControl {
  private readonly requestsService = inject(PrintRequestsService);

  readonly request = input.required<PrintRequest>();
  readonly isAdmin = input(false);
  readonly changed = output<PrintRequest>();

  protected readonly confirming = signal(false);
  protected readonly reason = signal('');
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly maxReason = MAX_CANCEL_REASON;

  protected readonly available = computed(() => {
    return !this.isAdmin() && allowsCancelling(this.request().status);
  });

  protected ask(): void {
    this.error.set(null);
    this.reason.set('');
    this.confirming.set(true);
  }

  protected setReason(event: Event): void {
    this.reason.set((event.target as HTMLTextAreaElement).value);
  }

  protected keep(): void {
    this.confirming.set(false);
  }

  protected confirm(): void {
    if (this.reason().length > MAX_CANCEL_REASON) {
      this.error.set(`Reason must be at most ${MAX_CANCEL_REASON} characters`);
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.requestsService.cancel(this.request().id, this.reason()).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.confirming.set(false);
        this.changed.emit(updated);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(errorMessage(err, 'Could not cancel the request.'));
      },
    });
  }

  protected dismissError(): void {
    this.error.set(null);
  }
}
