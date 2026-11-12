import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { SlicingSettings } from '../../../core/models/settings.model';
import { SlicerPrinter } from '../../../core/models/slicer.model';
import { SettingsService } from '../settings.service';
import { SLICING_OFF } from '../settings-page.spec';
import { SlicingSettingsCard } from './slicing-settings';

describe('SlicingSettingsCard', () => {
  const settingsService = {
    slicerStatus: vi.fn(),
    printers: vi.fn(),
    profiles: vi.fn(),
    updateSlicing: vi.fn(),
  };
  const P1S: SlicerPrinter = {
    model: 'Bambu Lab P1S',
    vendor: 'BBL',
    nozzles: [0.2, 0.4, 0.6].map((diameter) => {
      return { diameter, machine: `P1S ${diameter}`, bed: { x: 256, y: 256, z: 250 } };
    }),
  };
  const MINI: SlicerPrinter = {
    model: 'Mini',
    vendor: 'X',
    nozzles: [{ diameter: 0.25, machine: 'Mini 0.25', bed: { x: 180, y: 180, z: 180 } }],
  };
  const NOZZLELESS: SlicerPrinter = { model: 'Odd', vendor: 'X', nozzles: [] };
  const REACHABLE = { configured: true, reachable: true, orcaVersion: '2.5.0', message: null };
  const PROFILES = { machine: 'm', processes: ['0.20mm Standard'], filaments: ['PLA Basic'] };
  const SAVED: SlicingSettings = {
    enabled: true,
    timeoutMinutes: 10,
    printer: 'Bambu Lab P1S',
    ownedNozzles: [0.2, 0.4],
    defaultNozzle: 0.4,
    process: '0.20mm Standard',
    filament: 'PLA Basic',
  };

  function httpError(message?: string) {
    let error: { message: string } | null = null;
    if (message) {
      error = { message };
    }
    return throwError(() => {
      return new HttpErrorResponse({ status: 503, error });
    });
  }

  function create(settings: SlicingSettings = SLICING_OFF): SlicingSettingsCard {
    const fixture = TestBed.createComponent(SlicingSettingsCard);
    fixture.componentRef.setInput('settings', settings);
    const card = fixture.componentInstance;
    card.ngOnInit();
    return card;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    settingsService.slicerStatus.mockReturnValue(of(REACHABLE));
    settingsService.printers.mockReturnValue(of([MINI, P1S, NOZZLELESS]));
    settingsService.profiles.mockReturnValue(of(PROFILES));
    TestBed.configureTestingModule({
      providers: [{ provide: SettingsService, useValue: settingsService }],
    });
  });

  describe('loading', () => {
    it('loads status, printers and the saved printer profiles', () => {
      const card = create(SAVED);

      expect(card['statusText']()).toEqual({
        ok: true,
        text: 'Slicer reachable · OrcaSlicer 2.5.0',
      });
      expect(card['printerOptions']()).toEqual(['Mini', 'Bambu Lab P1S', 'Odd']);
      expect(card['nozzleOptions']()).toEqual([0.2, 0.4, 0.6]);
      expect(card['isOwned'](0.2)).toBe(true);
      expect(card['isOwned'](0.6)).toBe(false);
      expect(settingsService.profiles).toHaveBeenCalledWith('Bambu Lab P1S', 0.4);
      expect(card['profiles']()).toEqual(PROFILES);
    });

    it('keeps the saved choices when the slicer is unreachable', () => {
      settingsService.slicerStatus.mockReturnValue(
        of({ configured: true, reachable: false, orcaVersion: null, message: "Can't reach it" }),
      );
      const card = create(SAVED);

      expect(card['statusText']()).toEqual({ ok: false, text: "Can't reach it" });
      expect(settingsService.printers).not.toHaveBeenCalled();
      expect(card['printerOptions']()).toEqual(['Bambu Lab P1S']);
      expect(card['nozzleOptions']()).toEqual([0.2, 0.4]);
      expect(settingsService.profiles).not.toHaveBeenCalled();
    });

    it('reports a failing status check, printer list or profile list', () => {
      settingsService.slicerStatus.mockReturnValue(httpError());
      expect(create()['statusText']()).toEqual({ ok: false, text: 'Could not check the slicer' });

      settingsService.slicerStatus.mockReturnValue(of(REACHABLE));
      settingsService.printers.mockReturnValue(httpError());
      expect(create()['error']()).toBe('Could not load the printers from the slicer.');

      settingsService.printers.mockReturnValue(of([P1S]));
      settingsService.profiles.mockReturnValue(httpError('Unknown printer'));
      expect(create(SAVED)['error']()).toBe('Unknown printer');
    });

    it('shows nothing before the status is known and a generic text without a message', () => {
      settingsService.slicerStatus.mockReturnValue(
        of({ configured: false, reachable: false, orcaVersion: null, message: null }),
      );
      expect(create()['statusText']()).toEqual({ ok: false, text: 'The slicer is not reachable' });
    });
  });

  describe('choosing', () => {
    it('preselects the 0.4 mm nozzle of a new printer and offers its bed', () => {
      const card = create();
      const beds = vi.fn();
      card.bedSuggested.subscribe(beds);

      card['form'].controls.printer.setValue('Bambu Lab P1S');
      card['onPrinterChange']();

      expect(card['ownedNozzles']()).toEqual([0.4]);
      expect(card['defaultNozzle']()).toBe(0.4);
      expect(beds).toHaveBeenCalledWith({ printer: 'Bambu Lab P1S', x: 256, y: 256, z: 250 });
      expect(settingsService.profiles).toHaveBeenCalledWith('Bambu Lab P1S', 0.4);
    });

    it('takes the first nozzle when there is no 0.4 mm one, and nothing without nozzles', () => {
      const card = create();
      card['form'].controls.printer.setValue('Mini');
      card['onPrinterChange']();
      expect(card['defaultNozzle']()).toBe(0.25);

      card['form'].controls.printer.setValue('Odd');
      card['onPrinterChange']();
      expect(card['ownedNozzles']()).toEqual([]);
      expect(card['defaultNozzle']()).toBeNull();
    });

    it('keeps the default nozzle among the owned ones', () => {
      const card = create(SAVED);

      card['toggleNozzle'](0.6, true);
      expect(card['ownedNozzles']()).toEqual([0.2, 0.4, 0.6]);
      expect(card['defaultNozzle']()).toBe(0.4);

      card['toggleNozzle'](0.4, false);
      expect(card['ownedNozzles']()).toEqual([0.2, 0.6]);
      expect(card['defaultNozzle']()).toBe(0.2);
      expect(card['form'].controls.process.value).toBe('');

      card['toggleNozzle'](0.2, false);
      card['toggleNozzle'](0.6, false);
      expect(card['defaultNozzle']()).toBeNull();
    });

    it('reloads the profiles for another default nozzle', () => {
      const card = create(SAVED);

      card['onDefaultNozzleChange']('0.2');

      expect(card['defaultNozzle']()).toBe(0.2);
      expect(settingsService.profiles).toHaveBeenLastCalledWith('Bambu Lab P1S', 0.2);
    });
  });

  describe('saving', () => {
    it('saves the settings', () => {
      const card = create(SAVED);
      const saved = vi.fn();
      card.saved.subscribe(saved);
      const response = { buildPlate: { x: null, y: null, z: null }, slicing: SAVED };
      settingsService.updateSlicing.mockReturnValue(of(response));

      card['save']();

      expect(settingsService.updateSlicing).toHaveBeenCalledWith(SAVED);
      expect(saved).toHaveBeenCalledWith(response);
      expect(card['notice']()).toBe('Slicing settings saved.');
    });

    it('saves "off" without a printer', () => {
      const card = create();
      settingsService.updateSlicing.mockReturnValue(of({}));

      card['save']();

      expect(settingsService.updateSlicing).toHaveBeenCalledWith(SLICING_OFF);
    });

    it('checks the rules before saving', () => {
      const card = create(SAVED);

      card['form'].controls.timeoutMinutes.setValue('61');
      card['save']();
      expect(card['error']()).toBe('The timeout must be a whole number of minutes from 1 to 60');

      card['form'].controls.timeoutMinutes.setValue('10');
      card['form'].controls.filament.setValue('Something else');
      card['save']();
      expect(card['error']()).toBe('Choose a filament profile from the list');

      card['form'].controls.process.setValue('');
      card['save']();
      expect(card['error']()).toBe(
        'Choose a printer, nozzles and profiles before switching slicing on',
      );
      expect(settingsService.updateSlicing).not.toHaveBeenCalled();
    });

    it('shows a save error', () => {
      const card = create(SAVED);
      settingsService.updateSlicing.mockReturnValue(httpError('Bad'));

      card['save']();

      expect(card['error']()).toBe('Bad');
      expect(card['saving']()).toBe(false);
    });
  });
});
