import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Observable } from 'rxjs';
import { errorMessage } from '../../../../core/http/api-error';
import { PrintRequest, RequestStatus } from '../../../../core/models/print-request.model';
import {
  allowsPayment,
  isFinalStatus,
  statusLabel,
} from '../../../../shared/print-request-display';
import { formatEuro, formatPriceInput, MAX_PRICE, parsePrice } from '../../../../shared/price';
import { PrintRequestsService } from '../../print-requests.service';

/** A readable price ("69,42", "6.900,42", "69.42", ...) within the backend's range. */
function validPrice(control: AbstractControl<string>): ValidationErrors | null {
  if (control.value.trim() === '') {
    return null;
  }
  const price = parsePrice(control.value);
  if (price === null) {
    return { priceFormat: true };
  }
  if (price > MAX_PRICE) {
    return { priceMax: true };
  }
  return null;
}

/**
 * Admin review: a Jira-style status dropdown offering only the allowed next statuses, the final
 * price, and paid. Final statuses (declined, completed) need confirmation. On a completed request
 * only paid can still change.
 */
@Component({
  selector: 'app-review-panel',
  imports: [ReactiveFormsModule],
  templateUrl: './review-panel.html',
})
export class ReviewPanel {
  private readonly requestsService = inject(PrintRequestsService);
  private readonly fb = inject(FormBuilder);

  readonly request = input.required<PrintRequest>();
  /** Emits the updated request after every successful change. */
  readonly changed = output<PrintRequest>();

  protected readonly status = this.fb.nonNullable.control<RequestStatus>('SUBMITTED');
  protected readonly declineReason = this.fb.nonNullable.control('', Validators.maxLength(2000));
  /** Text, not a number input: browsers only accept their own locale's decimal separator. */
  protected readonly price = this.fb.nonNullable.control('', [Validators.required, validPrice]);

  /** A final status picked in the dropdown, waiting for confirmation. */
  protected readonly pendingStatus = signal<RequestStatus | null>(null);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  /** Status and price can only change while the request isn't final. */
  protected readonly editable = computed(() => {
    return this.request().nextStatuses.length > 0;
  });

  protected readonly canPay = computed(() => {
    return allowsPayment(this.request().status);
  });

  protected readonly paid = computed(() => {
    return this.request().paidAt !== null;
  });

  /** The current status first, then the statuses it may move to. */
  protected readonly statusOptions = computed(() => {
    const request = this.request();
    return [request.status, ...request.nextStatuses];
  });

  /** Completing and paying need a price (the backend refuses them otherwise). */
  protected readonly needsPrice = computed(() => {
    return this.request().finalPrice === null;
  });

  /**
   * What the controls show. A new request object with the same review state (the detail page
   * reloads the request while files are being sliced) keeps it, so typed input isn't wiped.
   */
  private readonly reviewState = computed(
    () => {
      const request = this.request();
      return {
        id: request.id,
        status: request.status,
        finalPrice: request.finalPrice,
        paidAt: request.paidAt,
        updatedAt: request.updatedAt,
      };
    },
    {
      equal: (previous, next) => {
        return (
          previous.id === next.id &&
          previous.status === next.status &&
          previous.finalPrice === next.finalPrice &&
          previous.paidAt === next.paidAt &&
          previous.updatedAt === next.updatedAt
        );
      },
    },
  );

  protected readonly statusLabel = statusLabel;

  constructor() {
    // Keep the controls in sync with the request shown (initially and after every change).
    effect(() => {
      const request = this.reviewState();
      this.status.setValue(request.status, { emitEvent: false });
      this.price.setValue(this.priceText(request.finalPrice), { emitEvent: false });
      this.price.markAsUntouched();
      // While paid the price is locked, so the paid amount stays correct.
      if (request.paidAt !== null) {
        this.price.disable({ emitEvent: false });
      } else {
        this.price.enable({ emitEvent: false });
      }
    });
    this.status.valueChanges.pipe(takeUntilDestroyed()).subscribe((status) => {
      this.onStatusSelected(status);
    });
  }

  protected isOptionDisabled(status: RequestStatus): boolean {
    return status === 'COMPLETED' && this.needsPrice();
  }

  protected onStatusSelected(status: RequestStatus): void {
    this.notice.set(null);
    this.error.set(null);
    if (status === this.request().status) {
      this.pendingStatus.set(null);
      return;
    }
    if (isFinalStatus(status)) {
      this.declineReason.reset();
      this.pendingStatus.set(status);
      return;
    }
    this.applyStatus(status, null);
  }

  protected confirmPending(): void {
    const status = this.pendingStatus();
    if (!status || this.declineReason.invalid) {
      return;
    }
    let reason: string | null = null;
    if (status === 'DECLINED') {
      reason = this.declineReason.value;
    }
    this.applyStatus(status, reason);
  }

  protected cancelPending(): void {
    this.pendingStatus.set(null);
    this.resetStatus();
  }

  protected savePrice(): void {
    this.price.markAsTouched();
    const price = parsePrice(this.price.value);
    if (this.price.invalid || price === null) {
      return;
    }
    this.save(
      this.requestsService.changePrice(this.request().id, price),
      `Price set to ${formatEuro(price)}.`,
      'Could not save the price.',
    );
  }

  protected clearPrice(): void {
    this.save(
      this.requestsService.changePrice(this.request().id, null),
      'Price cleared.',
      'Could not save the price.',
    );
  }

  protected togglePaid(): void {
    const paid = !this.paid();
    let notice = 'Marked as unpaid.';
    if (paid) {
      notice = 'Marked as paid.';
    }
    this.save(
      this.requestsService.changePaid(this.request().id, paid),
      notice,
      'Could not change the paid state.',
    );
  }

  /** User-facing text for the price input's first error, once touched. */
  protected priceError(): string | null {
    const errors = this.price.errors;
    if (!errors || !this.price.touched) {
      return null;
    }
    if (errors['required']) {
      return 'Enter a price';
    }
    if (errors['priceMax']) {
      return `Price must be at most ${formatEuro(MAX_PRICE)}`;
    }
    return 'Enter a price like 69,42 or 6.900,42';
  }

  private applyStatus(status: RequestStatus, declineReason: string | null): void {
    this.saving.set(true);
    this.status.disable({ emitEvent: false });
    this.requestsService.changeStatus(this.request().id, status, declineReason).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.status.enable({ emitEvent: false });
        this.pendingStatus.set(null);
        this.notice.set(`Status changed to ${statusLabel(updated.status)}.`);
        this.changed.emit(updated);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.status.enable({ emitEvent: false });
        this.pendingStatus.set(null);
        this.resetStatus();
        this.error.set(errorMessage(err, 'Could not change the status.'));
      },
    });
  }

  /** Runs a price/paid change and reports the result. */
  private save(change: Observable<PrintRequest>, notice: string, failure: string): void {
    this.saving.set(true);
    this.notice.set(null);
    this.error.set(null);
    change.subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.notice.set(notice);
        this.changed.emit(updated);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(errorMessage(err, failure));
      },
    });
  }

  private priceText(price: number | null): string {
    if (price === null) {
      return '';
    }
    return formatPriceInput(price);
  }

  private resetStatus(): void {
    this.status.setValue(this.request().status, { emitEvent: false });
  }
}
