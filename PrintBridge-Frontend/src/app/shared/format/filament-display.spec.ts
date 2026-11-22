import { RED_FOR_WHITE, SPOOLMAN_RED } from '../../../testing/fixtures';
import {
  choiceLabel,
  filamentBackground,
  filamentName,
  gramsLeftLabel,
  isChoiceFor,
  notEnoughFor,
  originalLabel,
} from './filament-display';

describe('filament display', () => {
  const NOTHING = { name: null, vendor: null, material: null };

  it('names a filament with what is known', () => {
    expect(filamentName(SPOOLMAN_RED)).toBe('Acme PLA Basic Red – PLA');
    expect(filamentName({ ...NOTHING, material: 'PETG' })).toBe('Filament – PETG');
    expect(filamentName({ ...NOTHING, vendor: 'Acme' })).toBe('Acme');
    expect(filamentName(NOTHING)).toBe('Filament');
  });

  it('splits multi-colour filaments into equal parts', () => {
    expect(filamentBackground('#FF0000', [])).toBe('#FF0000');
    expect(filamentBackground(null, ['#00FF00'])).toBe('#00FF00');
    expect(filamentBackground(null, ['#FF0000', '#0000FF'])).toBe(
      'linear-gradient(90deg, #FF0000 0% 50%, #0000FF 50% 100%)',
    );
    expect(filamentBackground(null, [])).toBeNull();
  });

  it('describes what the file says', () => {
    const slot = { wholeItem: false, plateIndex: 1, originalType: 'PLA', originalColor: '#FFFFFF' };
    expect(originalLabel(slot)).toBe('PLA #FFFFFF');
    expect(originalLabel({ ...slot, originalColor: null })).toBe('PLA');
    expect(originalLabel({ ...slot, originalType: null })).toBe('#FFFFFF');
    expect(
      originalLabel({ wholeItem: true, plateIndex: null, originalType: null, originalColor: null }),
    ).toBe('file colour');
  });

  it('labels a choice with the file colour it replaces', () => {
    expect(choiceLabel(RED_FOR_WHITE)).toBe('Acme PLA Basic Red – PLA (was PLA #FFFFFF)');
    const whole = {
      ...RED_FOR_WHITE,
      wholeItem: true,
      plateIndex: null,
      originalType: null,
      originalColor: null,
    };
    expect(choiceLabel(whole)).toBe('Acme PLA Basic Red – PLA');
    expect(choiceLabel({ ...whole, inSpoolman: false })).toBe(
      'Acme PLA Basic Red – PLA · no longer in Spoolman',
    );
  });

  it('says how much is left and whether it is enough', () => {
    expect(gramsLeftLabel(SPOOLMAN_RED)).toBe('412 g left');
    expect(gramsLeftLabel({ ...SPOOLMAN_RED, gramsLeft: null })).toBeNull();

    expect(notEnoughFor(SPOOLMAN_RED, 412)).toBe(false);
    expect(notEnoughFor(SPOOLMAN_RED, 413)).toBe(true);
    expect(notEnoughFor(SPOOLMAN_RED, null)).toBe(false);
    expect(notEnoughFor({ ...SPOOLMAN_RED, gramsLeft: 0 }, null)).toBe(true);
    expect(notEnoughFor({ ...SPOOLMAN_RED, gramsLeft: null }, 5000)).toBe(false);
  });

  it('matches a choice to its plate and colour', () => {
    const slot = { wholeItem: false, plateIndex: 1, originalType: 'PLA', originalColor: '#ffffff' };
    expect(isChoiceFor(RED_FOR_WHITE, slot)).toBe(true);
    expect(isChoiceFor(RED_FOR_WHITE, { ...slot, plateIndex: 2 })).toBe(false);
    expect(isChoiceFor(RED_FOR_WHITE, { ...slot, originalType: 'PETG' })).toBe(false);
    expect(isChoiceFor(RED_FOR_WHITE, { ...slot, originalColor: null })).toBe(false);
    const whole = { wholeItem: true, plateIndex: null, originalType: null, originalColor: null };
    expect(isChoiceFor({ ...RED_FOR_WHITE, ...whole }, whole)).toBe(true);
  });
});
