import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable, of, Subject, throwError } from 'rxjs';
import {
  ReslicePrinter,
  ResliceOptions,
  SlicerProfiles,
} from '../../../../core/models/slicer.model';
import { REQUEST } from '../../../../../testing/fixtures';
import { SettingsService } from '../../../settings/settings.service';
import { PrintRequestsService } from '../../print-requests.service';
import { PROJECT_FILAMENTS, PROJECT_OWN, ResliceDialog } from './reslice-dialog';

describe('ResliceDialog', () => {
  const P1S: ReslicePrinter = {
    id: 1,
    model: 'Bambu Lab P1S',
    ownedNozzles: [0.2, 0.4],
    defaultNozzle: 0.4,
    process: '0.20mm Standard @BBL P1S',
    filament: 'Bambu PLA Basic @BBL P1S',
  };
  const A1: ReslicePrinter = {
    id: 2,
    model: 'Bambu Lab A1',
    ownedNozzles: [0.4, 0.6],
    defaultNozzle: 0.6,
    process: null,
    filament: null,
  };
  const STL_OPTIONS: ResliceOptions = {
    printers: [P1S, A1],
    project: false,
    projectPrinter: null,
    projectNozzle: null,
    start: {
      printerId: 1,
      nozzle: 0.4,
      process: P1S.process,
      filament: P1S.filament,
      custom: false,
    },
  };
  const PROJECT_OPTIONS: ResliceOptions = {
    ...STL_OPTIONS,
    project: true,
    projectPrinter: 'bambu lab p1s ',
    projectNozzle: 0.2,
    start: { printerId: 1, nozzle: 0.2, process: null, filament: null, custom: false },
  };

  const requestsService = { resliceOptions: vi.fn(), resliceWith: vi.fn() };
  const settingsService = { profiles: vi.fn() };
  const modal = { close: vi.fn(), dismiss: vi.fn() };
  let dialog: ResliceDialog;

  function profiles(processes: string[], filaments: string[]): Observable<SlicerProfiles> {
    return of({ machine: 'm', processes, filaments });
  }

  function open(options: ResliceOptions | Observable<ResliceOptions>): ResliceDialog {
    if (options instanceof Observable) {
      requestsService.resliceOptions.mockReturnValue(options);
    } else {
      requestsService.resliceOptions.mockReturnValue(of(options));
    }
    const component = TestBed.createComponent(ResliceDialog).componentInstance;
    component.requestId = 4;
    component.fileId = 7;
    component.filename = 'benchy.stl';
    component.ngOnInit();
    return component;
  }

  function select(handler: (event: Event) => void, value: string): void {
    handler({ target: { value } } as unknown as Event);
  }

  beforeEach(() => {
    vi.clearAllMocks();
    settingsService.profiles.mockImplementation((printer: string) => {
      if (printer === 'Bambu Lab A1') {
        return profiles(['0.30mm Standard @A1'], ['PLA @A1']);
      }
      return profiles(
        ['0.12mm Fine @BBL P1S', '0.20mm Standard @BBL P1S'],
        ['Bambu PLA Basic @BBL P1S', 'PETG HF'],
      );
    });
    TestBed.configureTestingModule({
      providers: [
        { provide: PrintRequestsService, useValue: requestsService },
        { provide: SettingsService, useValue: settingsService },
        { provide: NgbActiveModal, useValue: modal },
      ],
    });
  });

  it('starts at the last-used printer, nozzle and profiles for an STL', () => {
    dialog = open(STL_OPTIONS);

    expect(requestsService.resliceOptions).toHaveBeenCalledWith(4, 7);
    expect(settingsService.profiles).toHaveBeenCalledWith('Bambu Lab P1S', 0.4);
    expect(dialog['loading']()).toBe(false);
    expect(dialog['printer']()).toEqual(P1S);
    expect(dialog['nozzle']()).toBe(0.4);
    expect(dialog['process']()).toBe(P1S.process);
    expect(dialog['filament']()).toBe(P1S.filament);
    expect(dialog['ownSettingsPossible']()).toBe(false);
    expect(dialog['projectFilamentsPossible']()).toBe(false);
    expect(dialog['canSlice']()).toBe(true);
  });

  it('offers the project own settings only on its own printer and nozzle', () => {
    dialog = open(PROJECT_OPTIONS);
    expect(dialog['ownSettingsPossible']()).toBe(true);
    expect(dialog['process']()).toBe(PROJECT_OWN);
    expect(dialog['filament']()).toBe(PROJECT_FILAMENTS);
    expect(dialog['canSlice']()).toBe(true);

    select(dialog['onNozzleChange'].bind(dialog), '0.4');

    expect(settingsService.profiles).toHaveBeenLastCalledWith('Bambu Lab P1S', 0.4);
    expect(dialog['ownSettingsPossible']()).toBe(false);
    expect(dialog['process']()).toBe(P1S.process);
    // The project's filaments stay possible on any printer.
    expect(dialog['filament']()).toBe(PROJECT_FILAMENTS);
  });

  it('reloads the profiles for another printer and keeps the nozzle size only if it owns it', () => {
    dialog = open(STL_OPTIONS);
    select(dialog['onPrinterChange'].bind(dialog), '2');

    expect(dialog['printer']()).toEqual(A1);
    expect(dialog['nozzle']()).toBe(0.4);
    expect(settingsService.profiles).toHaveBeenLastCalledWith('Bambu Lab A1', 0.4);
    // A1 has no own profiles here, so the first listed ones are picked.
    expect(dialog['process']()).toBe('0.30mm Standard @A1');
    expect(dialog['filament']()).toBe('PLA @A1');

    select(dialog['onNozzleChange'].bind(dialog), '0.6');
    select(dialog['onPrinterChange'].bind(dialog), '1');
    expect(dialog['nozzle']()).toBe(0.4);
    expect(settingsService.profiles).toHaveBeenLastCalledWith('Bambu Lab P1S', 0.4);
  });

  it('keeps a chosen profile while it still fits', () => {
    dialog = open(STL_OPTIONS);
    select(dialog['onProcessChange'].bind(dialog), '0.12mm Fine @BBL P1S');
    select(dialog['onFilamentInput'].bind(dialog), 'PETG HF');
    select(dialog['onNozzleChange'].bind(dialog), '0.2');

    expect(dialog['process']()).toBe('0.12mm Fine @BBL P1S');
    expect(dialog['filament']()).toBe('PETG HF');
  });

  it("only accepts a listed filament, or the project's filaments for a project", () => {
    dialog = open(STL_OPTIONS);
    select(dialog['onFilamentInput'].bind(dialog), 'PET');
    expect(dialog['filamentValid']()).toBe(false);
    expect(dialog['filamentError']()).toBe('Choose a filament profile from the list');
    expect(dialog['canSlice']()).toBe(false);

    select(dialog['onFilamentInput'].bind(dialog), 'PETG HF');
    expect(dialog['filamentError']()).toBeNull();
    expect(dialog['canSlice']()).toBe(true);

    select(dialog['onFilamentInput'].bind(dialog), PROJECT_FILAMENTS);
    expect(dialog['filamentValid']()).toBe(false);

    select(dialog['onFilamentInput'].bind(dialog), '  ');
    expect(dialog['filamentError']()).toBeNull();
    expect(dialog['canSlice']()).toBe(false);
  });

  it('cannot slice an STL without both profiles', () => {
    settingsService.profiles.mockReturnValue(profiles([], []));
    dialog = open(STL_OPTIONS);
    expect(dialog['process']()).toBe(PROJECT_OWN);
    expect(dialog['canSlice']()).toBe(false);
    dialog['slice']();
    expect(requestsService.resliceWith).not.toHaveBeenCalled();
  });

  it('slices with the choice and closes with the updated request', () => {
    dialog = open(PROJECT_OPTIONS);
    const answer = new Subject<typeof REQUEST>();
    requestsService.resliceWith.mockReturnValue(answer);
    select(dialog['onFilamentInput'].bind(dialog), 'PETG HF');

    dialog['slice']();

    expect(requestsService.resliceWith).toHaveBeenCalledWith(4, 7, {
      printerId: 1,
      nozzle: 0.2,
      process: null,
      filament: 'PETG HF',
    });
    expect(dialog['saving']()).toBe(true);
    expect(dialog['canSlice']()).toBe(false);
    answer.next(REQUEST);
    expect(modal.close).toHaveBeenCalledWith(REQUEST);
  });

  it("sends the project's filaments as null", () => {
    dialog = open(PROJECT_OPTIONS);
    requestsService.resliceWith.mockReturnValue(of(REQUEST));
    dialog['slice']();
    expect(requestsService.resliceWith).toHaveBeenCalledWith(4, 7, {
      printerId: 1,
      nozzle: 0.2,
      process: null,
      filament: null,
    });
  });

  it('shows why slicing was refused and stays open', () => {
    dialog = open(STL_OPTIONS);
    requestsService.resliceWith.mockReturnValue(
      throwError(() => {
        return new HttpErrorResponse({
          status: 409,
          error: { message: 'This file is already being sliced' },
        });
      }),
    );

    dialog['slice']();

    expect(dialog['saveError']()).toBe('This file is already being sliced');
    expect(dialog['saving']()).toBe(false);
    expect(modal.close).not.toHaveBeenCalled();
  });

  it('shows an error instead of the form when the options or profiles cannot be loaded', () => {
    dialog = open(
      throwError(() => {
        return new HttpErrorResponse({ status: 500 });
      }),
    );
    expect(dialog['loading']()).toBe(false);
    expect(dialog['loadError']()).toBeTruthy();

    settingsService.profiles.mockReturnValue(
      throwError(() => {
        return new HttpErrorResponse({
          status: 503,
          error: { message: "The slicer can't be reached" },
        });
      }),
    );
    dialog = open(STL_OPTIONS);
    expect(dialog['profilesError']()).toBe("The slicer can't be reached");
    expect(dialog['canSlice']()).toBe(false);
  });

  it('asks for a printer first when none is set up', () => {
    dialog = open({ ...STL_OPTIONS, printers: [] });
    expect(dialog['loadError']()).toBe(
      'Add a printer with nozzles and profiles in Settings first.',
    );
    expect(settingsService.profiles).not.toHaveBeenCalled();
  });

  it('falls back to the first printer and its default nozzle without a start', () => {
    dialog = open({
      ...STL_OPTIONS,
      start: { printerId: null, nozzle: null, process: null, filament: null, custom: false },
    });
    expect(dialog['printer']()).toEqual(P1S);
    expect(dialog['nozzle']()).toBe(0.4);
    expect(dialog['process']()).toBe(P1S.process);
  });

  it('can be cancelled', () => {
    dialog = open(STL_OPTIONS);
    dialog['cancel']();
    expect(modal.dismiss).toHaveBeenCalled();
  });
});
