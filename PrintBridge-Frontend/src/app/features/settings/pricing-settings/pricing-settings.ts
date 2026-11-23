import { Component, computed, inject, input, OnInit, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../../core/http/api-error';
import { PricingSettings, Settings } from '../../../core/models/settings.model';
import {
  formatPrice,
  formatPriceInput,
  getCurrencyOptions,
  getCurrencySymbol,
  parsePrice,
} from '../../../shared/format/price';
import { SettingsService } from '../settings.service';

/** Same limits as the backend. */
export const MIN_MARKUP = 0.01;
export const MAX_MARKUP = 100;
export const MAX_PRICE_PER_100_GRAMS = 1000;

/** Shown as an example of a markup. */
const EXAMPLE_MARKUP = 1.5;

/**
 * Prices: the currency of every price (#75; changing it doesn't convert amounts), and the price
 * estimate (#72): the markup multiplies every price, and the price per 100 g is the default for
 * everything without a Spoolman price. Whether Spoolman's prices are used is switched in the
 * Spoolman tab.
 */
@Component({
  selector: 'app-pricing-settings',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './pricing-settings.html',
})
export class PricingSettingsCard implements OnInit {
  private readonly settingsService = inject(SettingsService);
  private readonly fb = inject(FormBuilder);

  readonly settings = input.required<PricingSettings>();
  /** Spoolman prices are in use (switched on, with the integration on). */
  readonly spoolmanPrices = input(false);
  readonly saved = output<Settings>();

  protected readonly form = this.fb.nonNullable.group({
    currency: ['EUR'],
    markup: [''],
    pricePer100Grams: [''],
  });
  /** The currency picked in the form (not saved yet), for the price per 100 g's symbol. */
  protected readonly selectedCurrency = signal('EUR');
  protected readonly currencies = computed(() => {
    return getCurrencyOptions(this.settings().currency);
  });
  protected readonly currencySymbol = computed(() => {
    return getCurrencySymbol(this.selectedCurrency());
  });
  protected readonly markupExample = formatPriceInput(EXAMPLE_MARKUP);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  ngOnInit(): void {
    const settings = this.settings();
    this.form.setValue({
      currency: settings.currency,
      markup: formatPriceInput(settings.markup),
      pricePer100Grams: formatPriceInput(settings.pricePer100Grams),
    });
    this.selectedCurrency.set(settings.currency);
  }

  protected onCurrencyChange(): void {
    this.selectedCurrency.set(this.form.controls.currency.value);
  }

  protected save(): void {
    this.notice.set(null);
    this.error.set(null);
    const value = this.form.getRawValue();
    const markup = parsePrice(value.markup);
    const pricePer100Grams = parsePrice(value.pricePer100Grams);
    if (markup === null || markup < MIN_MARKUP || markup > MAX_MARKUP) {
      this.error.set(
        `Markup must be a number from ${formatPriceInput(MIN_MARKUP)} to ${MAX_MARKUP} ` +
          'with at most 2 decimals',
      );
      return;
    }
    if (pricePer100Grams === null || pricePer100Grams > MAX_PRICE_PER_100_GRAMS) {
      const maximum = formatPrice(MAX_PRICE_PER_100_GRAMS, value.currency);
      this.error.set(`Price per 100 g must be from 0 to ${maximum} with at most 2 decimals`);
      return;
    }
    this.saving.set(true);
    this.settingsService
      .updatePricing({ currency: value.currency, markup, pricePer100Grams })
      .subscribe({
        next: (settings) => {
          this.saving.set(false);
          this.notice.set('Pricing saved.');
          this.saved.emit(settings);
        },
        error: (err: unknown) => {
          this.saving.set(false);
          this.error.set(errorMessage(err, 'Could not save the pricing.'));
        },
      });
  }
}
