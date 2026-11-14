import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { Printer, Settings } from '../../../core/models/settings.model';
import { SAVED_P1S } from '../../../core/testing';
import { SettingsService } from '../settings.service';
import { PrintersCard } from './printers-card';

describe('PrintersCard', () => {
  const settingsService = { deletePrinter: vi.fn(), makeDefaultPrinter: vi.fn() };
  const MINI: Printer = {
    id: 2,
    model: 'Bambu Lab A1 mini',
    buildPlate: { x: 180, y: 180, z: 180 },
    ownedNozzles: [],
    defaultNozzle: null,
    process: null,
    filament: null,
  };
  const SETTINGS: Settings = {
    slicing: { enabled: true, timeoutMinutes: 10 },
    defaultPrinterId: 1,
    printers: [SAVED_P1S, MINI],
    uploads: { maxFiles: 10, maxFileSizeMb: 100, ceilingMb: 100 },
  };

  function create(): PrintersCard {
    const fixture = TestBed.createComponent(PrintersCard);
    fixture.componentRef.setInput('settings', SETTINGS);
    return fixture.componentInstance;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [{ provide: SettingsService, useValue: settingsService }],
    });
  });

  it('describes each printer', () => {
    const card = create();

    expect(card['printers']()).toEqual([SAVED_P1S, MINI]);
    expect(card['slicingEnabled']()).toBe(true);
    expect(card['isDefault'](SAVED_P1S)).toBe(true);
    expect(card['isDefault'](MINI)).toBe(false);
    expect(card['plateText'](SAVED_P1S)).toBe('256 × 256 × 250 mm');
    expect(card['nozzleText'](SAVED_P1S)).toBe('0,2 mm · 0,4 mm (default)');
    expect(card['nozzleText'](MINI)).toBe('No nozzles chosen');
  });

  it('adds and edits one printer at a time', () => {
    const card = create();
    const changed = vi.fn();
    card.changed.subscribe(changed);

    card['startAdding']();
    expect(card['editing']()).toBe('new');
    expect(card['editingPrinter']()).toBeNull();
    card['stopEditing']();
    expect(card['editing']()).toBeNull();

    card['startEditing'](MINI);
    expect(card['editingPrinter']()).toEqual(MINI);
    card['onSaved'](SETTINGS);
    expect(card['editing']()).toBeNull();
    expect(card['notice']()).toBe('Printer saved.');
    expect(changed).toHaveBeenCalledWith(SETTINGS);
  });

  it('removes a printer after confirming', () => {
    const card = create();
    const changed = vi.fn();
    card.changed.subscribe(changed);
    const after = { ...SETTINGS, printers: [SAVED_P1S] };
    settingsService.deletePrinter.mockReturnValue(of(after));

    card['askDelete'](MINI);
    expect(card['confirmingDeleteId']()).toBe(2);
    card['cancelDelete']();
    expect(card['confirmingDeleteId']()).toBeNull();

    card['askDelete'](MINI);
    card['deletePrinter'](MINI);

    expect(settingsService.deletePrinter).toHaveBeenCalledWith(2);
    expect(changed).toHaveBeenCalledWith(after);
    expect(card['notice']()).toBe('Bambu Lab A1 mini removed.');
    expect(card['confirmingDeleteId']()).toBeNull();
    expect(card['busy']()).toBe(false);
  });

  it('makes another printer the default', () => {
    const card = create();
    settingsService.makeDefaultPrinter.mockReturnValue(of({ ...SETTINGS, defaultPrinterId: 2 }));

    card['makeDefault'](MINI);

    expect(settingsService.makeDefaultPrinter).toHaveBeenCalledWith(2);
    expect(card['notice']()).toBe('Bambu Lab A1 mini is now the default printer.');
  });

  it('shows why a change was refused', () => {
    const card = create();
    settingsService.deletePrinter.mockReturnValue(
      throwError(() => {
        return new HttpErrorResponse({
          status: 409,
          error: { message: 'Switch server slicing off before removing your last printer' },
        });
      }),
    );
    settingsService.makeDefaultPrinter.mockReturnValue(
      throwError(() => {
        return new HttpErrorResponse({ status: 500 });
      }),
    );

    card['deletePrinter'](SAVED_P1S);
    expect(card['error']()).toBe('Switch server slicing off before removing your last printer');

    card['makeDefault'](MINI);
    expect(card['error']()).toBe('Could not change the printer.');
    expect(card['busy']()).toBe(false);
  });
});
