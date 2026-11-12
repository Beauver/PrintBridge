import { formatCurrency, formatNumber, registerLocaleData } from '@angular/common';
import localeNl from '@angular/common/locales/nl';

/*
 * Prices are shown the Dutch way (€ 6.900,42) for everyone, while the rest of the UI stays
 * English. Registering here means every use of these helpers has the locale data available.
 */
registerLocaleData(localeNl);

const PRICE_LOCALE = 'nl';

/** Highest price the backend accepts (NUMERIC(10,2)). */
export const MAX_PRICE = 99_999_999.99;

/** "€ 6.900,42" */
export function formatEuro(value: number): string {
  return formatCurrency(value, PRICE_LOCALE, '€', 'EUR', '1.2-2');
}

/** "6.900,42": the value as it appears in the price input. */
export function formatPriceInput(value: number): string {
  return formatNumber(value, PRICE_LOCALE, '1.2-2');
}

/**
 * Reads a typed price in Dutch or English style: "69,42", "69.42", "6.900,42", "6,900.42",
 * "6900". Prices have at most 2 decimals, so a separator followed by exactly 3 digits can only be
 * a thousands separator ("6.900" = 6900). Returns null for anything else, including empty input.
 */
export function parsePrice(text: string): number | null {
  const compact = text.replace(/[\s€]/g, '');
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
