import { formatDecimal, getBrowserLocale } from './locale';

// Numbers follow the viewer's browser locale (#86), like prices; the examples are en-US.

/** "120 × 80.5 × 40 mm" (at most one decimal). */
export function formatDimensions(
  width: number,
  depth: number,
  height: number,
  locale = getBrowserLocale(),
): string {
  const sizes = [width, depth, height].map((size) => {
    return formatDecimal(size, 0, 1, locale);
  });
  return `${sizes.join(' × ')} mm`;
}

/** "63.2 cm³" */
export function formatVolume(cm3: number, locale = getBrowserLocale()): string {
  return `${formatDecimal(cm3, 0, 1, locale)} cm³`;
}

/** "33.69 g" for exact values; estimates are whole grams. */
export function formatGrams(grams: number, locale = getBrowserLocale()): string {
  return `${formatDecimal(grams, 0, 2, locale)} g`;
}

/** "0.4 mm" */
export function formatNozzle(mm: number, locale = getBrowserLocale()): string {
  return `${formatDecimal(mm, 1, 2, locale)} mm`;
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
