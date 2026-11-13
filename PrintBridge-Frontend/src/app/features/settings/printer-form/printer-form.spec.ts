import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { Printer } from '../../../core/models/settings.model';
import { SlicerPrinter } from '../../../core/models/slicer.model';
import { SAVED_P1S, SLICER_P1S } from '../../../core/testing';
import { SettingsService } from '../settings.service';
import { PrinterForm } from './printer-form';

describe('PrinterForm', () => {
  const settingsService = { profiles: vi.fn(), addPrinter: vi.fn(), updatePrinter: vi.fn() };
  const MINI: SlicerPrinter = {
    model: 'Mini',
    vendor: 'X',
    nozzles: [{ diameter: 0.25, machine: 'Mini 0.25', bed: { x: 180, y: 180, z: 180 } }],
  };
  const NOZZLELESS: SlicerPrinter = { model: 'Odd', vendor: 'X', nozzles: [] };
  const PROFILES = { machine: 'm', processes: ['0.20mm Standard'], filaments: ['PLA Basic'] };
  const SLICER = [MINI, SLICER_P1S, NOZZLELESS];

  function httpError(message?: string) {
    let error: { message: string } | null = null;
    if (message) {
      error = { message };
    }
    return throwError(() => {
      return new HttpErrorResponse({ status: 400, error });
    });
  }

  function create(
    printer: Printer | null = null,
    slicerPrinters: SlicerPrinter[] = SLICER,
    slicingEnabled = false,
  ): PrinterForm {
    const fixture = TestBed.createComponent(PrinterForm);
    fixture.componentRef.setInput('printer', printer);
    fixture.componentRef.setInput('slicerPrinters', slicerPrinters);
    fixture.componentRef.setInput('slicingEnabled', slicingEnabled);
    const form = fixture.componentInstance;
    form.ngOnInit();
    return form;
  }

  function choose(form: PrinterForm, model: string): void {
    form['form'].controls.model.setValue(model);
    form['onModelChange']();
  }

  beforeEach(() => {
    vi.clearAllMocks();
    settingsService.profiles.mockReturnValue(of(PROFILES));
    TestBed.configureTestingModule({
      providers: [{ provide: SettingsService, useValue: settingsService }],
    });
  });

  describe('loading', () => {
    it('starts empty for a new printer', () => {
      const form = create();

      expect(form['isNew']()).toBe(true);
      expect(form['chooseModel']()).toBe(true);
      expect(form['modelOptions']()).toEqual(['Mini', 'Bambu Lab P1S', 'Odd']);
      expect(form['nozzleOptions']()).toEqual([]);
      expect(settingsService.profiles).not.toHaveBeenCalled();
    });

    it('shows a saved printer with its profiles', () => {
      const form = create(SAVED_P1S);

      expect(form['isNew']()).toBe(false);
      expect(form['form'].getRawValue()).toEqual({
        model: 'Bambu Lab P1S',
        x: '256',
        y: '256',
        z: '250',
        process: '0.20mm Standard',
        filament: 'PLA Basic',
      });
      expect(form['nozzleOptions']()).toEqual([0.2, 0.4, 0.6]);
      expect(form['isOwned'](0.2)).toBe(true);
      expect(form['isOwned'](0.6)).toBe(false);
      expect(settingsService.profiles).toHaveBeenCalledWith('Bambu Lab P1S', 0.4);
      expect(form['profiles']()).toEqual(PROFILES);
    });

    it('lets the model be typed while the slicer is unreachable', () => {
      const form = create(SAVED_P1S, []);

      expect(form['chooseModel']()).toBe(false);
      expect(form['modelOptions']()).toEqual(['Bambu Lab P1S']);
      expect(form['nozzleOptions']()).toEqual([0.2, 0.4]);
      expect(settingsService.profiles).not.toHaveBeenCalled();
    });

    it('keeps a saved model the slicer does not list', () => {
      const form = create({ ...SAVED_P1S, model: 'My own printer' });

      expect(form['modelOptions']()[0]).toBe('My own printer');
    });

    it('reports a failing profile list', () => {
      settingsService.profiles.mockReturnValue(httpError('Unknown printer'));

      expect(create(SAVED_P1S)['error']()).toBe('Unknown printer');
    });
  });

  describe('choosing', () => {
    it('preselects the 0.4 mm nozzle of a new model and fills in its build plate', () => {
      const form = create();

      choose(form, 'Bambu Lab P1S');

      expect(form['ownedNozzles']()).toEqual([0.4]);
      expect(form['defaultNozzle']()).toBe(0.4);
      expect(form['form'].getRawValue()).toMatchObject({ x: '256', y: '256', z: '250' });
      expect(form['plateFilled']()).toBe(true);
      expect(settingsService.profiles).toHaveBeenCalledWith('Bambu Lab P1S', 0.4);
    });

    it('takes the first nozzle when there is no 0.4 mm one, and nothing without nozzles', () => {
      const form = create();
      choose(form, 'Mini');
      expect(form['defaultNozzle']()).toBe(0.25);

      choose(form, 'Odd');
      expect(form['ownedNozzles']()).toEqual([]);
      expect(form['defaultNozzle']()).toBeNull();
      expect(form['plateFilled']()).toBe(false);
    });

    it('keeps the default nozzle among the owned ones', () => {
      const form = create(SAVED_P1S);

      form['toggleNozzle'](0.6, true);
      expect(form['ownedNozzles']()).toEqual([0.2, 0.4, 0.6]);
      expect(form['defaultNozzle']()).toBe(0.4);

      form['toggleNozzle'](0.4, false);
      expect(form['ownedNozzles']()).toEqual([0.2, 0.6]);
      expect(form['defaultNozzle']()).toBe(0.2);
      expect(form['form'].controls.process.value).toBe('');

      form['toggleNozzle'](0.2, false);
      form['toggleNozzle'](0.6, false);
      expect(form['defaultNozzle']()).toBeNull();
    });

    it('reloads the profiles for another default nozzle', () => {
      const form = create(SAVED_P1S);

      form['onDefaultNozzleChange']('0.2');

      expect(form['defaultNozzle']()).toBe(0.2);
      expect(settingsService.profiles).toHaveBeenLastCalledWith('Bambu Lab P1S', 0.2);
    });

    it('explains size errors once touched', () => {
      const form = create();
      const controls = form['form'].controls;

      expect(form['sizeError'](controls.x)).toBeNull();
      controls.x.markAsTouched();
      expect(form['sizeError'](controls.x)).toBe('Required');
      controls.y.setValue('2001');
      controls.y.markAsDirty();
      expect(form['sizeError'](controls.y)).toBe('A whole number from 1 to 2000');
      controls.z.setValue('100');
      controls.z.markAsTouched();
      expect(form['sizeError'](controls.z)).toBeNull();
    });
  });

  describe('saving', () => {
    it('adds a new printer', () => {
      const form = create();
      const saved = vi.fn();
      form.saved.subscribe(saved);
      settingsService.addPrinter.mockReturnValue(of({ printers: [] }));
      choose(form, 'Bambu Lab P1S');
      form['form'].patchValue({ process: '0.20mm Standard', filament: 'PLA Basic' });

      form['save']();

      expect(settingsService.addPrinter).toHaveBeenCalledWith({
        model: 'Bambu Lab P1S',
        buildPlate: { x: 256, y: 256, z: 250 },
        ownedNozzles: [0.4],
        defaultNozzle: 0.4,
        process: '0.20mm Standard',
        filament: 'PLA Basic',
      });
      expect(saved).toHaveBeenCalledWith({ printers: [] });
      expect(form['saving']()).toBe(false);
    });

    it('changes a saved printer', () => {
      const form = create(SAVED_P1S);
      settingsService.updatePrinter.mockReturnValue(of({}));

      form['save']();

      const { id, ...input } = SAVED_P1S;
      expect(settingsService.updatePrinter).toHaveBeenCalledWith(id, input);
    });

    it('saves a typed printer with only a build plate', () => {
      const form = create(null, []);
      settingsService.addPrinter.mockReturnValue(of({}));
      form['form'].patchValue({ model: ' My printer ', x: '200', y: '200', z: '200' });

      form['save']();

      expect(settingsService.addPrinter).toHaveBeenCalledWith({
        model: 'My printer',
        buildPlate: { x: 200, y: 200, z: 200 },
        ownedNozzles: [],
        defaultNozzle: null,
        process: null,
        filament: null,
      });
    });

    it('checks the rules before saving', () => {
      const form = create(SAVED_P1S, SLICER, true);
      const controls = form['form'].controls;

      controls.model.setValue(' ');
      form['save']();
      expect(form['error']()).toBe('Enter the printer model');

      controls.model.setValue('Bambu Lab P1S');
      controls.z.setValue('0');
      form['save']();
      expect(form['error']()).toBe('The build plate needs whole millimetres from 1 to 2000');

      controls.z.setValue('250');
      controls.filament.setValue('Something else');
      form['save']();
      expect(form['error']()).toBe('Choose a filament profile from the list');

      controls.process.setValue('');
      form['save']();
      expect(form['error']()).toBe(
        'Choose nozzles and profiles for this printer while server slicing is on',
      );
      expect(settingsService.updatePrinter).not.toHaveBeenCalled();
    });

    it('shows a save error', () => {
      const form = create(SAVED_P1S);
      settingsService.updatePrinter.mockReturnValue(httpError('Already there'));

      form['save']();

      expect(form['error']()).toBe('Already there');
      expect(form['saving']()).toBe(false);
    });

    it('can be cancelled', () => {
      const form = create();
      const cancelled = vi.fn();
      form.cancelled.subscribe(cancelled);

      form['cancel']();

      expect(cancelled).toHaveBeenCalled();
    });
  });
});
