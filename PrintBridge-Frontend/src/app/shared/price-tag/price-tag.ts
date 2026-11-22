import { Component, computed, input } from '@angular/core';
import { EstimatedPrice } from '../../core/models/print-request.model';
import { formatEuro } from '../format/price';

/**
 * A request's price: the final price (crossed out with a Paid badge once paid), else the estimate
 * (#72) as "~ € 12,34 (estimate)", else "Price pending".
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

  protected readonly formatted = computed(() => {
    const price = this.price();
    if (price === null) {
      return null;
    }
    return formatEuro(price);
  });

  protected readonly paid = computed(() => {
    return this.paidAt() !== null;
  });

  /** "~ € 12,34" with its note, while there is no final price. */
  protected readonly estimated = computed(() => {
    const estimate = this.estimate();
    if (this.price() !== null || estimate === null) {
      return null;
    }
    let note = '(estimate)';
    if (estimate.partial) {
      note = '(partial estimate)';
    }
    return { text: `~ ${formatEuro(estimate.amount)}`, note };
  });
}
