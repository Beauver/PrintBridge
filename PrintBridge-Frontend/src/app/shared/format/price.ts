import { getBrowserLocale } from './locale';

/*
 * Prices are in the currency the admin chose (#75) and formatted the way the viewer's browser
 * formats numbers: an American sees "$6,900.42", a Dutch viewer "US$ 6.900,42". Amounts always
 * have 2 decimals (that's how they are stored), also for currencies like JPY.
 */

/** Highest price the backend accepts (NUMERIC(10,2)). */
export const MAX_PRICE = 99_999_999.99;

/** Used when the browser can't list currencies (older browsers). */
const FALLBACK_CURRENCIES = [
  'AUD',
  'CAD',
  'CHF',
  'DKK',
  'EUR',
  'GBP',
  'JPY',
  'NOK',
  'PLN',
  'SEK',
  'USD',
];

/** A currency the admin can choose. */
export interface CurrencyOption {
  /** ISO 4217, e.g. "EUR". */
  code: string;
  /** "EUR – Euro" */
  label: string;
}

/** "$6,900.42" in en-US, "US$ 6.900,42" in nl. */
export function formatPrice(value: number, currency: string, locale = getBrowserLocale()): string {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
}

/**
 * "6900.42" in en-US, "6900,42" in nl: the value as it appears in a price input. No thousands
 * separators (not every locale groups by three, and the input must read back what it shows) and
 * always Western digits.
 */
export function formatPriceInput(value: number, locale = getBrowserLocale()): string {
  return new Intl.NumberFormat(locale, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
    useGrouping: false,
    numberingSystem: 'latn',
  }).format(value);
}

/** "$" for USD in en-US, "US$" in nl, "CHF" for CHF: the label in front of a price input. */
export function getCurrencySymbol(currency: string, locale = getBrowserLocale()): string {
  const parts = new Intl.NumberFormat(locale, { style: 'currency', currency }).formatToParts(0);
  const symbol = parts.find((part) => {
    return part.type === 'currency';
  });
  if (symbol) {
    return symbol.value;
  }
  return currency;
}

/**
 * Every currency the browser knows, sorted by code, with its name in the viewer's language. The
 * current currency is always included, even when the browser doesn't list it.
 */
export function getCurrencyOptions(current: string, locale = getBrowserLocale()): CurrencyOption[] {
  const codes = new Set(getSupportedCurrencies());
  codes.add(current);
  const names = new Intl.DisplayNames([locale], { type: 'currency' });
  return [...codes].sort().map((code) => {
    return { code, label: currencyLabel(code, names) };
  });
}

function getSupportedCurrencies(): string[] {
  if (typeof Intl.supportedValuesOf === 'function') {
    return Intl.supportedValuesOf('currency');
  }
  return FALLBACK_CURRENCIES;
}

function currencyLabel(code: string, names: Intl.DisplayNames): string {
  const name = names.of(code);
  if (!name || name === code) {
    return code;
  }
  return `${code} – ${name}`;
}

/**
 * Reads a typed price in Dutch or English style: "69,42", "69.42", "6.900,42", "6,900.42",
 * "1'234.50", "6900", also with a currency symbol or code ("$ 12", "12,34 €", "CHF 5"). Prices
 * have at most 2 decimals, so a separator followed by exactly 3 digits can only be a thousands
 * separator ("6.900" = 6900). Returns null for anything else, including empty input.
 */
export function parsePrice(text: string): number | null {
  const compact = text
    .replace(/[\s'’]/g, '')
    .replace(/^[\p{L}\p{Sc}]+/u, '')
    .replace(/[\p{L}\p{Sc}]+$/u, '');
  if (!/^[0-9.,]+$/.test(compact)) {
    return null;
  }

  let integerPart = compact;
  let decimals = '';
  let decimalSeparator = '';
  const decimalMatch = /^(.*)([.,])(\d{1,2})$/.exec(compact);
  if (decimalMatch) {
    integerPart = decimalMatch[1];
    decimalSeparator = decimalMatch[2];
    decimals = decimalMatch[3];
  }

  const digits = integerDigits(integerPart, decimalSeparator);
  if (digits === null) {
    return null;
  }
  if (decimals === '') {
    return Number(digits);
  }
  return Number(`${digits}.${decimals}`);
}

/** The digits of the part before the decimals, with valid thousands grouping removed. */
function integerDigits(integerPart: string, decimalSeparator: string): string | null {
  if (/^\d+$/.test(integerPart)) {
    return integerPart;
  }
  const grouped = /^\d{1,3}(([.,])\d{3})(\2\d{3})*$/.exec(integerPart);
  if (!grouped) {
    return null;
  }
  const groupSeparator = grouped[2];
  if (groupSeparator === decimalSeparator) {
    return null;
  }
  return integerPart.split(groupSeparator).join('');
}
