import { Component, computed, input } from '@angular/core';
import { formatEuro } from '../price';

/** A request's price: "Price pending", the price, or the price crossed out with a Paid badge. */
@Component({
  selector: 'app-price-tag',
  templateUrl: './price-tag.html',
})
export class PriceTag {
  readonly price = input.required<number | null>();
  readonly paidAt = input<string | null>(null);

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
}
