import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ModelInfo, PlateInfo } from '../../../../core/models/model-info.model';
import { STL_INFO } from '../../../../core/testing';
import { ModelInfoCard } from './model-info-card';

describe('ModelInfoCard', () => {
  const plate: PlateInfo = STL_INFO.plates[0];

  const BAMBU: ModelInfo = {
    status: 'ANALYZED',
    source: 'BAMBU',
    volumeCm3: 63.2,
    estimatedGrams: 27,
    slicedGrams: 65.25,
    printSeconds: 5720,
    fits: false,
    plates: [
      {
        ...plate,
        name: 'Lid',
        grams: 33.69,
        printSeconds: 2963,
        fits: false,
        filaments: [{ type: 'PLA', color: '#FFFFFF', grams: 33.69 }],
      },
      { ...plate, index: 2, width: null, depth: null, height: null, filaments: [] },
    ],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  function create(info: ModelInfo, isAdmin = false): ModelInfoCard {
    const fixture = TestBed.createComponent(ModelInfoCard);
    fixture.componentRef.setInput('info', info);
    fixture.componentRef.setInput('isAdmin', isAdmin);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('shows size, volume and the estimate for an STL', () => {
    const card = create(STL_INFO);

    expect(card['analyzed']()).toBe(true);
    expect(card['showPlates']()).toBe(false);
    expect(card['dimensions']()).toBe('20 × 10 × 5 mm');
    expect(card['volume']()).toBe('1 cm³');
    expect(card['weight']()).toEqual({ text: '~0 g', estimate: true });
    expect(card['printTime']()).toBeNull();
    expect(card['filaments']()).toEqual([]);
    expect(card['doesNotFit']()).toBe(false);
  });

  it('shows exact numbers and plates for a sliced project', () => {
    const card = create(BAMBU);

    expect(card['showPlates']()).toBe(true);
    expect(card['plateCount']()).toBe('2 plates');
    expect(card['weight']()).toEqual({ text: '65,25 g', estimate: false });
    expect(card['printTime']()).toBe('1 h 35 min');
    expect(card['doesNotFit']()).toBe(true);
  });

  it('describes each plate', () => {
    const card = create(BAMBU);
    const [lid, empty] = BAMBU.plates;

    expect(card['plateLabel'](lid)).toBe('1. Lid');
    expect(card['plateLabel'](empty)).toBe('Plate 2');
    expect(card['plateDimensions'](lid)).toBe('20 × 10 × 5 mm');
    expect(card['plateDimensions'](empty)).toBeNull();
    expect(card['plateGrams'](lid)).toBe('33,69 g');
    expect(card['plateGrams'](empty)).toBeNull();
    expect(card['plateTime'](lid)).toBe('49 min');
    expect(card['plateTime'](empty)).toBeNull();
  });

  it('labels filaments with what is known', () => {
    const card = create(BAMBU);

    expect(card['filamentLabel']({ type: 'PLA', color: '#FFFFFF', grams: 33.69 })).toBe(
      'PLA · #FFFFFF · 33,69 g',
    );
    expect(card['filamentLabel']({ type: null, color: null, grams: null })).toBe(
      'Unknown filament',
    );
  });

  it('lists the filaments of a single-plate PrusaSlicer file', () => {
    const filaments = [{ type: 'PETG', color: '#0000FF', grams: null }];
    const card = create({ ...STL_INFO, source: 'PRUSA', plates: [{ ...plate, filaments }] });

    expect(card['filaments']()).toEqual(filaments);
    expect(card['plateCount']()).toBe('1 plate');
  });

  it('handles pending and failed files', () => {
    const pending: ModelInfo = { ...STL_INFO, status: 'PENDING', volumeCm3: null, plates: [] };
    const card = create(pending, true);

    expect(card['analyzed']()).toBe(false);
    expect(card['dimensions']()).toBeNull();
    expect(card['filaments']()).toEqual([]);
    expect(card['volume']()).toBeNull();
    expect(card['showPlateSizeHint']()).toBe(false);
    expect(create({ ...pending, status: 'FAILED', estimatedGrams: null })['weight']()).toBeNull();
  });

  it('hints the admin to set the build plate size', () => {
    expect(create(STL_INFO, true)['showPlateSizeHint']()).toBe(true);
    expect(create(STL_INFO, false)['showPlateSizeHint']()).toBe(false);
    expect(create(BAMBU, true)['showPlateSizeHint']()).toBe(false);
  });
});
