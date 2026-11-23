import {
  formatPrice,
  formatPriceInput,
  getCurrencyOptions,
  getCurrencySymbol,
  parsePrice,
} from './price';

/** Intl puts (narrow) non-breaking spaces between symbol and amount. */
function plain(text: string): string {
  return text.replace(/\s/g, ' ');
}

describe('price helpers', () => {
  it('formats prices in the given locale and currency', () => {
    expect(formatPrice(6900.42, 'USD', 'en-US')).toBe('$6,900.42');
    expect(plain(formatPrice(6900.42, 'USD', 'nl'))).toBe('US$ 6.900,42');
    expect(plain(formatPrice(6900.42, 'EUR', 'nl'))).toBe('€ 6.900,42');
    expect(formatPrice(0, 'GBP', 'en-GB')).toBe('£0.00');
    // Always 2 decimals, also for currencies that normally have none.
    expect(formatPrice(1234, 'JPY', 'en-US')).toBe('¥1,234.00');
  });

  it('uses the browser locale by default', () => {
    expect(formatPrice(12.5, 'EUR')).toBe(formatPrice(12.5, 'EUR', navigator.languages[0]));
  });

  it('formats input values without grouping and with Western digits', () => {
    expect(formatPriceInput(6900.4, 'nl')).toBe('6900,40');
    expect(formatPriceInput(69.42, 'en-US')).toBe('69.42');
    expect(formatPriceInput(123456.5, 'en-IN')).toBe('123456.50');
    expect(formatPriceInput(5, 'ar-EG')).toMatch(/^5[^0-9]00$/);
  });

  it('knows each currency symbol', () => {
    expect(getCurrencySymbol('USD', 'en-US')).toBe('$');
    expect(getCurrencySymbol('USD', 'nl')).toBe('US$');
    expect(getCurrencySymbol('CHF', 'en-US')).toBe('CHF');
    // Unknown to the locale data: the code itself.
    expect(getCurrencySymbol('XTS', 'en-US')).toBe('XTS');
  });

  it('lists every currency with its name, including the current one', () => {
    const options = getCurrencyOptions('EUR', 'en');
    expect(options).toContainEqual({ code: 'EUR', label: 'EUR – Euro' });
    expect(options).toContainEqual({ code: 'USD', label: 'USD – US Dollar' });
    const codes = options.map((option) => {
      return option.code;
    });
    expect(codes).toEqual([...codes].sort());
    // A code the browser doesn't list (Java may know older ones) is still offered.
    expect(getCurrencyOptions('XXQ', 'en')).toContainEqual({ code: 'XXQ', label: 'XXQ' });
  });

  it('falls back to a short list when the browser cannot list currencies', () => {
    const supportedValuesOf = Intl.supportedValuesOf;
    Object.defineProperty(Intl, 'supportedValuesOf', { value: undefined, configurable: true });
    try {
      const codes = getCurrencyOptions('EUR', 'en').map((option) => {
        return option.code;
      });
      expect(codes).toContain('USD');
      expect(codes.length).toBeLessThan(20);
    } finally {
      Object.defineProperty(Intl, 'supportedValuesOf', {
        value: supportedValuesOf,
        configurable: true,
      });
    }
  });

  it('reads Dutch and English decimals', () => {
    expect(parsePrice('69,42')).toBe(69.42);
    expect(parsePrice('69.42')).toBe(69.42);
    expect(parsePrice('69,4')).toBe(69.4);
    expect(parsePrice('0,5')).toBe(0.5);
    expect(parsePrice('6900')).toBe(6900);
    expect(parsePrice(' € 12 ')).toBe(12);
  });

  it('ignores currency symbols, codes and apostrophe grouping', () => {
    expect(parsePrice('$ 12,34')).toBe(12.34);
    expect(parsePrice('US$ 12,34')).toBe(12.34);
    expect(parsePrice('12,34 €')).toBe(12.34);
    expect(parsePrice('CHF 5')).toBe(5);
    expect(parsePrice('120 kr')).toBe(120);
    expect(parsePrice("1'234.50")).toBe(1234.5);
    expect(parsePrice('1’234.50')).toBe(1234.5);
  });

  it('reads thousands separators in either style', () => {
    expect(parsePrice('6.900,42')).toBe(6900.42);
    expect(parsePrice('6,900.42')).toBe(6900.42);
    expect(parsePrice('6.900')).toBe(6900);
    expect(parsePrice('6,900')).toBe(6900);
    expect(parsePrice('1.000.000,5')).toBe(1000000.5);
    expect(parsePrice('6 900,42')).toBe(6900.42);
    // 3 digits after a separator always mean thousands (prices have at most 2 decimals).
    expect(parsePrice('69,423')).toBe(69423);
  });

  it('rejects anything else', () => {
    for (const text of [
      '',
      '  ',
      'abc',
      '-5',
      '69,4231',
      '6.90.0',
      '6.900.42',
      '6,900,42',
      '69.',
      ',5',
      '1,00.000',
      '12.3456',
      '$',
      '12$34',
    ]) {
      expect(parsePrice(text), text).toBeNull();
    }
  });
});
