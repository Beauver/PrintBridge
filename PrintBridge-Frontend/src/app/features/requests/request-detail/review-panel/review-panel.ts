import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Observable } from 'rxjs';
import { errorMessage } from '../../../../core/http/api-error';
import { PrintRequest } from '../../../../core/models/print-request.model';
import { allowsPayment } from '../../../../shared/format/print-request-display';
import {
  formatPrice,
  formatPriceInput,
  getCurrencySymbol,
  MAX_PRICE,
  parsePrice,
} from '../../../../shared/format/price';
import { PrintRequestsService } from '../../print-requests.service';

/** Shown as an example of how to type a price. */
const EXAMPLE_PRICE = 69.42;

/** A readable price ("69,42", "6.900,42", "69.42", "$ 12", ...) within the backend's range. */
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
 * Admin "Price & payment": the final price and paid. The status is changed from its badge next to
 * the request name (#65). On a completed request only paid can still change.
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

  /** Text, not a number input: browsers only accept their own locale's decimal separator. */
  protected readonly price = this.fb.nonNullable.control('', [Validators.required, validPrice]);

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  /** The price can only change while the request isn't final. */
  protected readonly editable = computed(() => {
    return this.request().nextStatuses.length > 0;
  });

  protected readonly canPay = computed(() => {
    return allowsPayment(this.request().status);
  });

  protected readonly paid = computed(() => {
    return this.request().paidAt !== null;
  });

  /** The estimate as the field's hint (#72), else an example of the format. */
  protected readonly pricePlaceholder = computed(() => {
    const estimate = this.request().estimatedPrice;
    if (estimate === null) {
      return formatPriceInput(EXAMPLE_PRICE);
    }
    return formatPriceInput(estimate.amount);
  });

  /** In front of the price input, e.g. "€" (#75). */
  protected readonly currencySymbol = computed(() => {
    return getCurrencySymbol(this.request().currency);
  });

  /** Paying needs a price (the backend refuses it otherwise). */
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

  constructor() {
    // Keep the controls in sync with the request shown (initially and after every change).
    effect(() => {
      const request = this.reviewState();
      this.price.setValue(this.priceText(request.finalPrice), { emitEvent: false });
      this.price.markAsUntouched();
      // While paid the price is locked, so the paid amount stays correct.
      if (request.paidAt !== null) {
        this.price.disable({ emitEvent: false });
      } else {
        this.price.enable({ emitEvent: false });
      }
    });
  }

  /** Saves the typed price; an empty field saves the estimate (#72) when there is one. */
  protected savePrice(): void {
    const estimate = this.request().estimatedPrice;
    if (this.price.value.trim() === '' && estimate !== null) {
      this.save(
        this.requestsService.changePrice(this.request().id, estimate.amount),
        `Price set to the estimate, ${this.format(estimate.amount)}.`,
        'Could not save the price.',
      );
      return;
    }
    this.price.markAsTouched();
    const price = parsePrice(this.price.value);
    if (this.price.invalid || price === null) {
      return;
    }
    this.save(
      this.requestsService.changePrice(this.request().id, price),
      `Price set to ${this.format(price)}.`,
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
      return `Price must be at most ${this.format(MAX_PRICE)}`;
    }
    return `Enter a price like ${formatPriceInput(EXAMPLE_PRICE)}`;
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

  private format(price: number): string {
    return formatPrice(price, this.request().currency);
  }

  private priceText(price: number | null): string {
    if (price === null) {
      return '';
    }
    return formatPriceInput(price);
  }
}
