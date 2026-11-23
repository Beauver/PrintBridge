import {
  formatDimensions,
  formatDuration,
  formatGrams,
  formatNozzle,
  formatVolume,
} from './model-info-display';

describe('model info display helpers', () => {
  it('formats dimensions in the browser locale (en-US in tests)', () => {
    expect(formatDimensions(120, 80.54, 1234)).toBe('120 × 80.5 × 1,234 mm');
  });

  it('formats dimensions the Dutch way in a Dutch browser', () => {
    expect(formatDimensions(120, 80.54, 1234, 'nl')).toBe('120 × 80,5 × 1.234 mm');
  });

  it('formats volume and grams', () => {
    expect(formatVolume(63.17)).toBe('63.2 cm³');
    expect(formatGrams(33.69)).toBe('33.69 g');
    expect(formatGrams(78)).toBe('78 g');
    expect(formatGrams(1234)).toBe('1,234 g');
    expect(formatVolume(63.17, 'nl')).toBe('63,2 cm³');
    expect(formatGrams(1234, 'nl')).toBe('1.234 g');
  });

  it('formats nozzle sizes', () => {
    expect(formatNozzle(0.4)).toBe('0.4 mm');
    expect(formatNozzle(0.25)).toBe('0.25 mm');
    expect(formatNozzle(0.4, 'nl')).toBe('0,4 mm');
  });

  it('formats print times', () => {
    expect(formatDuration(10)).toBe('1 min');
    expect(formatDuration(2963)).toBe('49 min');
    expect(formatDuration(7500)).toBe('2 h 5 min');
    expect(formatDuration(10800)).toBe('3 h');
  });
});
