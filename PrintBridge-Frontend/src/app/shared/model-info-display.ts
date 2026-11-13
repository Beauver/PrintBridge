import { formatNumber, registerLocaleData } from '@angular/common';
import localeNl from '@angular/common/locales/nl';

// Numbers are shown the Dutch way, like prices (see price.ts).
registerLocaleData(localeNl);

const LOCALE = 'nl';

/** "120 × 80,5 × 40 mm" (at most one decimal). */
export function formatDimensions(width: number, depth: number, height: number): string {
  const sizes = [width, depth, height].map((size) => {
    return formatNumber(size, LOCALE, '1.0-1');
  });
  return `${sizes.join(' × ')} mm`;
}

/** "63,2 cm³" */
export function formatVolume(cm3: number): string {
  return `${formatNumber(cm3, LOCALE, '1.0-1')} cm³`;
}

/** "33,69 g" for exact values; estimates are whole grams. */
export function formatGrams(grams: number): string {
  return `${formatNumber(grams, LOCALE, '1.0-2')} g`;
}

/** "0,4 mm" */
export function formatNozzle(mm: number): string {
  return `${formatNumber(mm, LOCALE, '1.1-2')} mm`;
}

/** "49 min", "2 h 5 min", "3 h"; under a minute counts as 1 min. */
export function formatDuration(seconds: number): string {
  const totalMinutes = Math.max(1, Math.round(seconds / 60));
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  if (hours === 0) {
    return `${minutes} min`;
  }
  if (minutes === 0) {
    return `${hours} h`;
  }
  return `${hours} h ${minutes} min`;
}
