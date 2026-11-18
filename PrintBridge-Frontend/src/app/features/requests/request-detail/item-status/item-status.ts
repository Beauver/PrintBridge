import { Component, inject, input, output, signal } from '@angular/core';
import { NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { errorMessage } from '../../../../core/http/api-error';
import { ItemStatus, PrintRequest, RequestFile } from '../../../../core/models/print-request.model';
import {
  itemStatusBadgeClass,
  itemStatusLabel,
} from '../../../../shared/format/print-request-display';
import { PrintRequestsService } from '../../print-requests.service';

/** Same limit as the backend. */
export const MAX_DECLINE_REASON = 500;

/**
 * The status of one file or link (#61), in its card header. Everyone sees the badge; for the
 * admin it is a dropdown with the allowed next statuses. Declining asks for confirmation with an
 * optional reason. Hands the updated request to the page.
 */
@Component({
  selector: 'app-item-status',
  imports: [NgbDropdownModule],
  templateUrl: './item-status.html',
  styleUrl: './item-status.css',
})
export class ItemStatusControl {
  private readonly requestsService = inject(PrintRequestsService);

  readonly requestId = input.required<number>();
  readonly item = input.required<RequestFile>();
  readonly isAdmin = input(false);
  readonly changed = output<PrintRequest>();

  protected readonly confirmingDecline = signal(false);
  protected readonly reason = signal('');
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly maxReason = MAX_DECLINE_REASON;
  protected readonly label = itemStatusLabel;
  protected readonly badgeClass = itemStatusBadgeClass;

  /** Declining asks first; any other status is saved right away. */
  protected choose(status: ItemStatus): void {
    if (status === 'DECLINED') {
      this.reason.set('');
      this.confirmingDecline.set(true);
      return;
    }
    this.save(status, null);
  }

  protected setReason(event: Event): void {
    this.reason.set((event.target as HTMLTextAreaElement).value);
  }

  protected confirmDecline(): void {
    let reason: string | null = null;
    if (this.reason().trim()) {
      reason = this.reason().trim();
    }
    this.save('DECLINED', reason);
  }

  protected cancelDecline(): void {
    this.confirmingDecline.set(false);
  }

  protected dismissError(): void {
    this.error.set(null);
  }

  private save(status: ItemStatus, reason: string | null): void {
    this.saving.set(true);
    this.error.set(null);
    this.requestsService
      .changeItemStatus(this.requestId(), this.item().id, status, reason)
      .subscribe({
        next: (request) => {
          this.saving.set(false);
          this.confirmingDecline.set(false);
          this.changed.emit(request);
        },
        error: (err: unknown) => {
          this.saving.set(false);
          this.error.set(errorMessage(err, 'Could not change the status.'));
        },
      });
  }
}
