/*
 * Numbers (prices #75, model info and file sizes #86) are formatted the way the viewer's browser
 * formats them; the rest of the UI stays English.
 */

/** The viewer's preferred locale, e.g. "en-US"; "en" when the browser doesn't say. */
export function getBrowserLocale(): string {
  if (typeof navigator === 'undefined') {
    return 'en';
  }
  if (navigator.languages && navigator.languages.length > 0) {
    return navigator.languages[0];
  }
  if (navigator.language) {
    return navigator.language;
  }
  return 'en';
}

/** A number with between `minimumDecimals` and `maximumDecimals` decimals, grouped the locale's way. */
export function formatDecimal(
  value: number,
  minimumDecimals: number,
  maximumDecimals: number,
  locale = getBrowserLocale(),
): string {
  return new Intl.NumberFormat(locale, {
    minimumFractionDigits: minimumDecimals,
    maximumFractionDigits: maximumDecimals,
  }).format(value);
}
