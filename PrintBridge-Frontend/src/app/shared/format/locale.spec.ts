import { formatDecimal, getBrowserLocale } from './locale';

describe('locale helpers', () => {
  it('uses the browser locale', () => {
    expect(getBrowserLocale()).toBe(navigator.languages[0]);
  });

  it('falls back to the single language, then English', () => {
    const languages = vi.spyOn(navigator, 'languages', 'get').mockReturnValue([]);
    const language = vi.spyOn(navigator, 'language', 'get').mockReturnValue('nl-NL');
    try {
      expect(getBrowserLocale()).toBe('nl-NL');
      language.mockReturnValue('');
      expect(getBrowserLocale()).toBe('en');
    } finally {
      languages.mockRestore();
      language.mockRestore();
    }
  });

  it('formats decimals in the given locale, grouped', () => {
    expect(formatDecimal(1234.567, 0, 2, 'en-US')).toBe('1,234.57');
    expect(formatDecimal(1234.567, 0, 2, 'nl')).toBe('1.234,57');
    expect(formatDecimal(5, 1, 1, 'en-US')).toBe('5.0');
    expect(formatDecimal(5, 0, 1)).toBe(formatDecimal(5, 0, 1, navigator.languages[0]));
  });
});
