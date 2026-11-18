import { Component, computed, inject, input, output, signal } from '@angular/core';
import { NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { errorMessage } from '../../../../core/http/api-error';
import { PrintRequest, RequestStatus } from '../../../../core/models/print-request.model';
import {
  isFinalStatus,
  statusBadgeClass,
  statusLabel,
} from '../../../../shared/format/print-request-display';
import { PrintRequestsService } from '../../print-requests.service';

/** Same limit as the backend. */
export const MAX_DECLINE_REASON = 2000;

/**
 * The request's status next to its name (#65). Everyone sees the badge; for the admin it is a
 * dropdown with the allowed next statuses, like the item statuses (#61). Final statuses
 * (declined, completed) ask for confirmation, declining with an optional reason; completing
 * needs a price. Hands the updated request to the page.
 */
@Component({
  selector: 'app-request-status',
  imports: [NgbDropdownModule],
  templateUrl: './request-status.html',
  styleUrl: './request-status.css',
})
export class RequestStatusControl {
  private readonly requestsService = inject(PrintRequestsService);

  readonly request = input.required<PrintRequest>();
  readonly isAdmin = input(false);
  readonly changed = output<PrintRequest>();

  /** A final status picked in the menu, waiting for confirmation. */
  protected readonly pendingStatus = signal<RequestStatus | null>(null);
  protected readonly reason = signal('');
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly maxReason = MAX_DECLINE_REASON;
  protected readonly label = statusLabel;
  protected readonly badgeClass = statusBadgeClass;

  /** The dropdown only while the admin can still move the request. */
  protected readonly editable = computed(() => {
    return this.isAdmin() && this.request().nextStatuses.length > 0;
  });

  /** Completing needs a price (the backend refuses it otherwise). */
  protected isDisabled(status: RequestStatus): boolean {
    return status === 'COMPLETED' && this.request().finalPrice === null;
  }

  protected choose(status: RequestStatus): void {
    this.error.set(null);
    if (this.isDisabled(status)) {
      return;
    }
    if (isFinalStatus(status)) {
      this.reason.set('');
      this.pendingStatus.set(status);
      return;
    }
    this.pendingStatus.set(null);
    this.apply(status, null);
  }

  protected setReason(event: Event): void {
    this.reason.set((event.target as HTMLTextAreaElement).value);
  }

  protected confirm(): void {
    const status = this.pendingStatus();
    if (!status || this.reason().length > MAX_DECLINE_REASON) {
      return;
    }
    let reason: string | null = null;
    if (status === 'DECLINED') {
      reason = this.reason();
    }
    this.apply(status, reason);
  }

  protected cancel(): void {
    this.pendingStatus.set(null);
  }

  protected dismissError(): void {
    this.error.set(null);
  }

  private apply(status: RequestStatus, declineReason: string | null): void {
    this.saving.set(true);
    this.requestsService.changeStatus(this.request().id, status, declineReason).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.pendingStatus.set(null);
        this.changed.emit(updated);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.pendingStatus.set(null);
        this.error.set(errorMessage(err, 'Could not change the status.'));
      },
    });
  }
}
