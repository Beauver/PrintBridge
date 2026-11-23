import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { PricingSettings, Settings } from '../../../core/models/settings.model';
import { NO_SETTINGS } from '../../../../testing/fixtures';
import { SettingsService } from '../settings.service';
import { PricingSettingsCard } from './pricing-settings';

describe('PricingSettingsCard', () => {
  const settingsService = { updatePricing: vi.fn() };
  let saved: Settings[];

  function create(
    pricing: PricingSettings = { currency: 'EUR', markup: 1.5, pricePer100Grams: 2.5 },
  ): PricingSettingsCard {
    const fixture = TestBed.createComponent(PricingSettingsCard);
    fixture.componentRef.setInput('settings', pricing);
    fixture.componentRef.setInput('spoolmanPrices', true);
    const card = fixture.componentInstance;
    card.saved.subscribe((settings) => {
      saved.push(settings);
    });
    card.ngOnInit();
    return card;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    saved = [];
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: SettingsService, useValue: settingsService }],
    });
  });

  it('fills the form in the browser locale (en-US in tests)', () => {
    const card = create();
    expect(card['form'].getRawValue()).toEqual({
      currency: 'EUR',
      markup: '1.50',
      pricePer100Grams: '2.50',
    });
    expect(card['currencySymbol']()).toBe('€');
    expect(card['markupExample']).toBe('1.50');
  });

  it('offers every currency and follows the picked one', () => {
    const card = create({ currency: 'CHF', markup: 1, pricePer100Grams: 0 });
    const codes = card['currencies']().map((currency) => {
      return currency.code;
    });
    expect(codes).toContain('CHF');
    expect(codes).toContain('USD');
    expect(card['currencySymbol']()).toBe('CHF');

    card['form'].controls.currency.setValue('USD');
    card['onCurrencyChange']();
    expect(card['currencySymbol']()).toBe('$');
  });

  it('saves the currency and both values, read in either style', () => {
    settingsService.updatePricing.mockReturnValue(of(NO_SETTINGS));
    const card = create();
    card['form'].setValue({ currency: 'GBP', markup: '1.75', pricePer100Grams: '3,10' });

    card['save']();

    expect(settingsService.updatePricing).toHaveBeenCalledWith({
      currency: 'GBP',
      markup: 1.75,
      pricePer100Grams: 3.1,
    });
    expect(card['notice']()).toBe('Pricing saved.');
    expect(saved).toEqual([NO_SETTINGS]);
  });

  it.each([
    ['0', '1', 'Markup must be a number from 0.01 to 100 with at most 2 decimals'],
    ['100,01', '1', 'Markup must be a number from 0.01 to 100 with at most 2 decimals'],
    ['abc', '1', 'Markup must be a number from 0.01 to 100 with at most 2 decimals'],
    ['1', '1000,01', 'Price per 100 g must be from 0 to €1,000.00 with at most 2 decimals'],
    ['1', '1,005', 'Price per 100 g must be from 0 to €1,000.00 with at most 2 decimals'],
  ])('refuses markup %s and price %s', (markup, pricePer100Grams, message) => {
    const card = create();
    card['form'].setValue({ currency: 'EUR', markup, pricePer100Grams });

    card['save']();

    expect(card['error']()).toBe(message);
    expect(settingsService.updatePricing).not.toHaveBeenCalled();
  });

  it('shows why saving failed', () => {
    settingsService.updatePricing.mockReturnValue(
      throwError(() => {
        return new HttpErrorResponse({
          status: 400,
          error: { message: 'Markup must be at most 100' },
        });
      }),
    );
    const card = create();

    card['save']();

    expect(card['error']()).toBe('Markup must be at most 100');
    expect(card['saving']()).toBe(false);
  });
});
