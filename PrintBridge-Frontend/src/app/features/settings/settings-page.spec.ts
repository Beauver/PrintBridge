import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { SettingsPage } from './settings-page';
import { SettingsService } from './settings.service';
import { NO_SETTINGS, SLICER_P1S } from '../../core/testing';

describe('SettingsPage', () => {
  const settingsService = { get: vi.fn(), slicerStatus: vi.fn(), slicerPrinters: vi.fn() };
  const REACHABLE = { configured: true, reachable: true, orcaVersion: '2.5.0', message: null };

  function serverError() {
    return throwError(() => {
      return new HttpErrorResponse({ status: 500 });
    });
  }

  function create(): SettingsPage {
    const page = TestBed.createComponent(SettingsPage).componentInstance;
    page.ngOnInit();
    return page;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    settingsService.get.mockReturnValue(of(NO_SETTINGS));
    settingsService.slicerStatus.mockReturnValue(of(REACHABLE));
    settingsService.slicerPrinters.mockReturnValue(of([SLICER_P1S]));
    TestBed.configureTestingModule({
      providers: [{ provide: SettingsService, useValue: settingsService }],
    });
  });

  it('loads the settings, the slicer status and its printers', () => {
    const page = create();

    expect(page['loading']()).toBe(false);
    expect(page['settings']()).toEqual(NO_SETTINGS);
    expect(page['slicerStatus']()).toEqual(REACHABLE);
    expect(page['slicerPrinters']()).toEqual([SLICER_P1S]);
  });

  it('does not ask an unreachable slicer for printers', () => {
    const down = { configured: true, reachable: false, orcaVersion: null, message: 'Down' };
    settingsService.slicerStatus.mockReturnValue(of(down));

    const page = create();

    expect(settingsService.slicerPrinters).not.toHaveBeenCalled();
    expect(page['slicerPrinters']()).toEqual([]);
  });

  it('shows load errors', () => {
    settingsService.get.mockReturnValue(serverError());
    settingsService.slicerStatus.mockReturnValue(serverError());

    const page = create();

    expect(page['error']()).toBe('Could not load the settings.');
    expect(page['loading']()).toBe(false);
    expect(page['slicerStatus']()?.message).toBe('Could not check the slicer');
  });

  it('reports a failing printer list', () => {
    settingsService.slicerPrinters.mockReturnValue(serverError());

    expect(create()['error']()).toBe('Could not load the printers from the slicer.');
  });

  it('keeps the latest settings after a change', () => {
    const page = create();
    const updated = { ...NO_SETTINGS, slicing: { enabled: false, timeoutMinutes: 20 } };

    page['onChanged'](updated);

    expect(page['settings']()).toEqual(updated);
  });
});
