import { formatDimensions, formatDuration, formatGrams, formatVolume } from './model-info-display';

describe('model info display helpers', () => {
  it('formats dimensions the Dutch way', () => {
    expect(formatDimensions(120, 80.54, 1234)).toBe('120 × 80,5 × 1.234 mm');
  });

  it('formats volume and grams', () => {
    expect(formatVolume(63.17)).toBe('63,2 cm³');
    expect(formatGrams(33.69)).toBe('33,69 g');
    expect(formatGrams(78)).toBe('78 g');
  });

  it('formats print times', () => {
    expect(formatDuration(10)).toBe('1 min');
    expect(formatDuration(2963)).toBe('49 min');
    expect(formatDuration(7500)).toBe('2 h 5 min');
    expect(formatDuration(10800)).toBe('3 h');
  });
});
