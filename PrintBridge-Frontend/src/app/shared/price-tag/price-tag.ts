import { Component, computed, input } from '@angular/core';
import { EstimatedPrice } from '../../core/models/print-request.model';
import { formatPrice } from '../format/price';

/**
 * A request's price: the final price (crossed out with a Paid badge once paid), else the estimate
 * (#72) as "~ €12.34 (estimate)", else "Price pending". Formatted in the viewer's locale (#75).
 */
@Component({
  selector: 'app-price-tag',
  templateUrl: './price-tag.html',
})
export class PriceTag {
  readonly price = input.required<number | null>();
  readonly paidAt = input<string | null>(null);
  /** Only shown while there is no final price. */
  readonly estimate = input<EstimatedPrice | null>(null);
  /** ISO 4217 code of the price and the estimate. */
  readonly currency = input.required<string>();

  protected readonly formatted = computed(() => {
    const price = this.price();
    if (price === null) {
      return null;
    }
    return formatPrice(price, this.currency());
  });

  protected readonly paid = computed(() => {
    return this.paidAt() !== null;
  });

  /** "~ €12.34" with its note, while there is no final price. */
  protected readonly estimated = computed(() => {
    const estimate = this.estimate();
    if (this.price() !== null || estimate === null) {
      return null;
    }
    let note = '(estimate)';
    if (estimate.partial) {
      note = '(partial estimate)';
    }
    return { text: `~ ${formatPrice(estimate.amount, this.currency())}`, note };
  });
}
