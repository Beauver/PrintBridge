import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Settings, SpoolmanSettings } from '../../../core/models/settings.model';
import { SpoolmanStatus } from '../../../core/models/spoolman.model';
import { NO_SETTINGS } from '../../../../testing/fixtures';
import { SettingsService } from '../settings.service';
import { SpoolmanSettingsCard } from './spoolman-settings';

describe('SpoolmanSettingsCard', () => {
  const settingsService = { updateSpoolman: vi.fn() };
  const CONNECTED: SpoolmanStatus = {
    configured: true,
    reachable: true,
    url: 'http://spoolman:8000',
    version: '0.22.1',
    message: null,
  };
  let saved: Settings[];

  function create(
    settings: SpoolmanSettings = {
      enabled: false,
      colorSelection: false,
      prices: false,
      notEnoughMessage: 'Order it',
    },
    status: SpoolmanStatus | null = CONNECTED,
  ): SpoolmanSettingsCard {
    const fixture = TestBed.createComponent(SpoolmanSettingsCard);
    fixture.componentRef.setInput('settings', settings);
    fixture.componentRef.setInput('status', status);
    const card = fixture.componentInstance;
    card.saved.subscribe((value) => {
      saved.push(value);
    });
    card.ngOnInit();
    return card;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    saved = [];
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: SettingsService, useValue: settingsService }],
    });
  });

  it('fills the switches from the settings', () => {
    const card = create({
      enabled: true,
      colorSelection: true,
      prices: true,
      notEnoughMessage: 'Order it',
    });
    expect(card['form'].getRawValue()).toEqual({
      enabled: true,
      colorSelection: true,
      prices: true,
      notEnoughMessage: 'Order it',
    });
    expect(card['integrationOn']()).toBe(true);
  });

  it('describes the connection', () => {
    expect(create()['statusText']()).toEqual({
      text: 'Connected to Spoolman 0.22.1 at http://spoolman:8000.',
      ok: true,
    });
    const down = { ...CONNECTED, reachable: false, version: null, message: "Can't connect" };
    expect(create(undefined, down)['statusText']()).toEqual({
      text: "Can't reach Spoolman at http://spoolman:8000: Can't connect",
      ok: false,
    });
    const missing = { ...CONNECTED, configured: false, reachable: false, url: null };
    const notConfigured = create(undefined, missing);
    expect(notConfigured['statusText']()).toEqual({
      text: 'Not configured: set SPOOLMAN_URL on the server.',
      ok: false,
    });
    expect(notConfigured['configured']()).toBe(false);

    const checking = create(undefined, null);
    expect(checking['statusText']()).toBeNull();
    expect(checking['configured']()).toBe(true);
  });

  it('switches colour selection off with the integration', () => {
    const card = create({
      enabled: true,
      colorSelection: true,
      prices: true,
      notEnoughMessage: 'Order it',
    });
    card['form'].controls.enabled.setValue(false);
    card['onIntegrationToggled']();
    expect(card['form'].getRawValue()).toEqual({
      enabled: false,
      colorSelection: false,
      prices: false,
      notEnoughMessage: 'Order it',
    });
    expect(card['integrationOn']()).toBe(false);

    card['form'].controls.enabled.setValue(true);
    card['onIntegrationToggled']();
    expect(card['integrationOn']()).toBe(true);
    expect(card['form'].controls.colorSelection.value).toBe(false);
  });

  it('saves the switches', () => {
    settingsService.updateSpoolman.mockReturnValue(of(NO_SETTINGS));
    const card = create();
    card['form'].setValue({
      enabled: true,
      colorSelection: true,
      prices: true,
      notEnoughMessage: 'Order it',
    });

    card['save']();

    expect(settingsService.updateSpoolman).toHaveBeenCalledWith({
      enabled: true,
      colorSelection: true,
      prices: true,
      notEnoughMessage: 'Order it',
    });
    expect(card['notice']()).toBe('Spoolman settings saved.');
    expect(card['saving']()).toBe(false);
    expect(saved).toEqual([NO_SETTINGS]);
  });

  it('refuses a warning text that is too long', () => {
    const card = create();
    card['form'].controls.notEnoughMessage.setValue('x'.repeat(301));

    card['save']();

    expect(card['error']()).toBe('The warning text must be at most 300 characters');
    expect(settingsService.updateSpoolman).not.toHaveBeenCalled();
  });

  it('shows why saving failed', () => {
    settingsService.updateSpoolman.mockReturnValue(
      throwError(() => {
        return new HttpErrorResponse({
          status: 400,
          error: { message: 'Set SPOOLMAN_URL on the server before switching Spoolman on' },
        });
      }),
    );
    const card = create();

    card['save']();

    expect(card['error']()).toBe('Set SPOOLMAN_URL on the server before switching Spoolman on');
    expect(card['saving']()).toBe(false);
    expect(saved).toEqual([]);
  });
});
