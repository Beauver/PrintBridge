import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { SettingsPage } from './settings-page';
import { SettingsService } from './settings.service';
import { NO_SETTINGS, SLICER_P1S } from '../../../testing/fixtures';

describe('SettingsPage', () => {
  const settingsService = {
    get: vi.fn(),
    slicerStatus: vi.fn(),
    slicerPrinters: vi.fn(),
    spoolmanStatus: vi.fn(),
  };
  const SPOOLMAN = {
    configured: true,
    reachable: true,
    url: 'http://spoolman:8000',
    version: '0.22.1',
    message: null,
  };
  const REACHABLE = { configured: true, reachable: true, orcaVersion: '2.5.0', message: null };

  function serverError() {
    return throwError(() => {
      return new HttpErrorResponse({ status: 500 });
    });
  }

  function create(tab?: string): SettingsPage {
    const fixture = TestBed.createComponent(SettingsPage);
    fixture.componentRef.setInput('tab', tab);
    const page = fixture.componentInstance;
    page.ngOnInit();
    return page;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    settingsService.get.mockReturnValue(of(NO_SETTINGS));
    settingsService.slicerStatus.mockReturnValue(of(REACHABLE));
    settingsService.slicerPrinters.mockReturnValue(of([SLICER_P1S]));
    settingsService.spoolmanStatus.mockReturnValue(of(SPOOLMAN));
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: SettingsService, useValue: settingsService }],
    });
  });

  it('loads the settings, the slicer status and its printers', () => {
    const page = create();

    expect(page['loading']()).toBe(false);
    expect(page['settings']()).toEqual(NO_SETTINGS);
    expect(page['slicerStatus']()).toEqual(REACHABLE);
    expect(page['slicerPrinters']()).toEqual([SLICER_P1S]);
    expect(page['spoolmanStatus']()).toEqual(SPOOLMAN);
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
    settingsService.spoolmanStatus.mockReturnValue(serverError());

    const page = create();

    expect(page['error']()).toBe('Could not load the settings.');
    expect(page['loading']()).toBe(false);
    expect(page['slicerStatus']()?.message).toBe('Could not check the slicer');
    expect(page['spoolmanStatus']()?.message).toBe('Could not check Spoolman');
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

  it('opens the tab in the URL, else the printers', () => {
    expect(create()['activeTab']()).toBe('printers');
    expect(create('spoolman')['activeTab']()).toBe('spoolman');
    expect(create('uploads')['activeTab']()).toBe('uploads');
    expect(create('nonsense')['activeTab']()).toBe('printers');
  });

  it('keeps the chosen tab in the URL', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    create()['selectTab']('slicing');

    expect(navigate).toHaveBeenCalledWith([], {
      queryParams: { tab: 'slicing' },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  });
});
