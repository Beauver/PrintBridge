import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  FilamentChoice,
  FilamentChoiceInput,
  FilamentSlot,
  SpoolmanFilament,
} from '../../../../core/models/spoolman.model';
import { RED_FOR_WHITE, SPOOLMAN_RED } from '../../../../../testing/fixtures';
import { SpoolmanFilamentsService } from '../../spoolman-filaments.service';
import { FilamentPicker } from './filament-picker';

describe('FilamentPicker', () => {
  const WHITE_PLA: FilamentSlot = { plateIndex: 1, originalType: 'PLA', originalColor: '#FFFFFF' };
  const WHOLE: FilamentSlot = { plateIndex: null, originalType: null, originalColor: null };
  const BLACK_PETG: SpoolmanFilament = {
    ...SPOOLMAN_RED,
    id: 4,
    name: 'Black',
    material: 'PETG',
    color: '#000000',
    gramsLeft: 0,
  };
  const filamentsService = {
    filaments: signal<SpoolmanFilament[] | null>(null),
    loading: signal(false),
    error: signal<string | null>(null),
    load: vi.fn(),
  };
  let chosen: FilamentChoiceInput[];
  let resets: number[];

  function create(
    slot: FilamentSlot,
    choice: FilamentChoice | null = null,
    editable = true,
    gramsNeeded: number | null = null,
  ): FilamentPicker {
    const fixture = TestBed.createComponent(FilamentPicker);
    fixture.componentRef.setInput('slot', slot);
    fixture.componentRef.setInput('choice', choice);
    fixture.componentRef.setInput('editable', editable);
    fixture.componentRef.setInput('gramsNeeded', gramsNeeded);
    const picker = fixture.componentInstance;
    picker.chosen.subscribe((value) => {
      chosen.push(value);
    });
    picker.reset.subscribe((value) => {
      resets.push(value);
    });
    fixture.detectChanges();
    return picker;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    chosen = [];
    resets = [];
    filamentsService.filaments.set([BLACK_PETG, SPOOLMAN_RED]);
    filamentsService.loading.set(false);
    filamentsService.error.set(null);
    TestBed.configureTestingModule({
      providers: [{ provide: SpoolmanFilamentsService, useValue: filamentsService }],
    });
  });

  it("shows the file's colour until a filament is chosen", () => {
    const picker = create(WHITE_PLA);
    expect(picker['background']()).toBe('#FFFFFF');
    expect(picker['label']()).toBe('PLA #FFFFFF');
    expect(picker['title']()).toBe('PLA #FFFFFF – click to choose a filament from Spoolman');
    expect(picker['notEnough']()).toBe(false);

    const chosenOne = create(WHITE_PLA, { ...RED_FOR_WHITE, notEnough: true }, false);
    expect(chosenOne['background']()).toBe('#FF0000');
    expect(chosenOne['title']()).toBe('Acme PLA Basic Red – PLA (was PLA #FFFFFF)');
    expect(chosenOne['notEnough']()).toBe(true);
  });

  it('asks to pick a filament for a whole item', () => {
    expect(create(WHOLE)['label']()).toBe('Pick filament');
    expect(create(WHOLE, null, false)['label']()).toBe('No filament chosen');
    expect(create(WHOLE)['background']()).toBeNull();
  });

  it('opens only while editable, and loads the list', () => {
    const readOnly = create(WHITE_PLA, null, false);
    readOnly['toggle']();
    expect(readOnly['open']()).toBe(false);
    expect(filamentsService.load).not.toHaveBeenCalled();

    const picker = create(WHITE_PLA);
    picker['toggle']();
    expect(picker['open']()).toBe(true);
    expect(filamentsService.load).toHaveBeenCalledWith();
    picker['toggle']();
    expect(picker['open']()).toBe(false);
  });

  it('starts every opening with an empty search', () => {
    const picker = create(WHITE_PLA);
    picker['onSearch']({ target: { value: 'petg' } } as unknown as Event);
    picker['onOpenChange'](true);
    expect(picker['search']()).toBe('');
    picker['onOpenChange'](false);
    expect(picker['open']()).toBe(false);
  });

  it('lists the same material first and marks too little left', () => {
    const picker = create(WHITE_PLA, null, true, 500);
    const options = picker['options']();
    expect(
      options.map((option) => {
        return option.filament.id;
      }),
    ).toEqual([3, 4]);
    expect(options[0]).toEqual({
      filament: SPOOLMAN_RED,
      name: 'Acme PLA Basic Red – PLA',
      background: '#FF0000',
      left: '412 g left',
      notEnough: true,
    });
    expect(options[1].notEnough).toBe(true);

    expect(create(WHITE_PLA, null, true, 100)['options']()[0].notEnough).toBe(false);
  });

  it('searches vendor, name and material', () => {
    const picker = create(WHITE_PLA);
    picker['onSearch']({ target: { value: ' petg ' } } as unknown as Event);
    expect(
      picker['options']().map((option) => {
        return option.filament.id;
      }),
    ).toEqual([4]);
    picker['onSearch']({ target: { value: 'nothing like it' } } as unknown as Event);
    expect(picker['options']()).toEqual([]);
    expect(picker['empty']()).toBe(false);

    filamentsService.filaments.set([]);
    expect(picker['empty']()).toBe(true);
  });

  it('emits the chosen filament for its slot and closes', () => {
    const picker = create(WHITE_PLA);
    picker['toggle']();
    picker['pick'](SPOOLMAN_RED);
    expect(chosen).toEqual([{ ...WHITE_PLA, spoolmanFilamentId: 3 }]);
    expect(picker['open']()).toBe(false);
  });

  it('goes back to the file colour', () => {
    create(WHITE_PLA)['useFileColour']();
    expect(resets).toEqual([]);
    create(WHITE_PLA, RED_FOR_WHITE)['useFileColour']();
    expect(resets).toEqual([12]);
  });

  it('retries a failed list', () => {
    create(WHITE_PLA)['retry']();
    expect(filamentsService.load).toHaveBeenCalledWith(true);
  });
});
