import { formatEuro, formatPriceInput, parsePrice } from './price';

describe('price helpers', () => {
  it('formats prices the Dutch way', () => {
    // Angular puts a non-breaking space between symbol and amount.
    expect(formatEuro(6900.42).replace(/\s/g, ' ')).toBe('€ 6.900,42');
    expect(formatEuro(0).replace(/\s/g, ' ')).toBe('€ 0,00');
    expect(formatPriceInput(6900.4)).toBe('6.900,40');
    expect(formatPriceInput(69.42)).toBe('69,42');
  });

  it('reads Dutch and English decimals', () => {
    expect(parsePrice('69,42')).toBe(69.42);
    expect(parsePrice('69.42')).toBe(69.42);
    expect(parsePrice('69,4')).toBe(69.4);
    expect(parsePrice('0,5')).toBe(0.5);
    expect(parsePrice('6900')).toBe(6900);
    expect(parsePrice(' € 12 ')).toBe(12);
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
    ]) {
      expect(parsePrice(text), text).toBeNull();
    }
  });
});
