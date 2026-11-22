import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Settings, SpoolmanSettings } from '../../../core/models/settings.model';
import { SpoolmanStatus } from '../../../core/models/spoolman.model';
import { NO_SETTINGS } from '../../../../testing/fixtures';
import { SettingsService } from '../settings.service';
import { presetOf, SpoolmanSettingsCard } from './spoolman-settings';

describe('SpoolmanSettingsCard', () => {
  const settingsService = { updateSpoolman: vi.fn() };
  const CONNECTED: SpoolmanStatus = {
    configured: true,
    reachable: true,
    url: 'http://spoolman:8000',
    version: '0.22.1',
    message: null,
  };
  const OFF: SpoolmanSettings = {
    enabled: false,
    colorSelection: false,
    prices: false,
    autoMatch: false,
    matchTolerance: 20,
    notEnoughMessage: 'Order it',
    noMatchMessage: 'Pick another',
  };
  const ALL_ON: SpoolmanSettings = {
    ...OFF,
    enabled: true,
    colorSelection: true,
    prices: true,
    autoMatch: true,
  };
  let saved: Settings[];

  function create(
    settings: SpoolmanSettings = OFF,
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

  it('fills the form from the settings', () => {
    const card = create({ ...ALL_ON, matchTolerance: 35 });
    expect(card['form'].getRawValue()).toEqual({
      enabled: true,
      colorSelection: true,
      prices: true,
      autoMatch: true,
      tolerancePreset: 'loose',
      customTolerance: '35',
      notEnoughMessage: 'Order it',
      noMatchMessage: 'Pick another',
    });
    expect(card['integrationOn']()).toBe(true);
    expect(card['selectionOn']()).toBe(true);
    expect(card['customTolerance']()).toBe(false);
    expect(create({ ...OFF, matchTolerance: 7 })['customTolerance']()).toBe(true);
  });

  it('maps tolerances to presets', () => {
    expect(presetOf(10)).toBe('strict');
    expect(presetOf(20)).toBe('normal');
    expect(presetOf(35)).toBe('loose');
    expect(presetOf(12)).toBe('custom');
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

  it('switches everything that needs the integration off with it', () => {
    const card = create(ALL_ON);
    card['form'].controls.enabled.setValue(false);
    card['onIntegrationToggled']();
    const value = card['form'].getRawValue();
    expect([value.colorSelection, value.prices, value.autoMatch]).toEqual([false, false, false]);
    expect(card['integrationOn']()).toBe(false);
    expect(card['selectionOn']()).toBe(false);

    card['form'].controls.enabled.setValue(true);
    card['onIntegrationToggled']();
    expect(card['integrationOn']()).toBe(true);
    expect(card['form'].controls.colorSelection.value).toBe(false);
  });

  it('switches automatic matching off with colour selection', () => {
    const card = create(ALL_ON);
    card['form'].controls.colorSelection.setValue(false);
    card['onColorSelectionToggled']();
    expect(card['form'].controls.autoMatch.value).toBe(false);
    expect(card['selectionOn']()).toBe(false);

    card['form'].controls.colorSelection.setValue(true);
    card['onColorSelectionToggled']();
    expect(card['selectionOn']()).toBe(true);
  });

  it('saves the settings with the tolerance of the preset or the custom number', () => {
    settingsService.updateSpoolman.mockReturnValue(of(NO_SETTINGS));
    const card = create(ALL_ON);
    card['form'].controls.tolerancePreset.setValue('strict');

    card['save']();

    expect(settingsService.updateSpoolman).toHaveBeenCalledWith({
      enabled: true,
      colorSelection: true,
      prices: true,
      autoMatch: true,
      matchTolerance: 10,
      notEnoughMessage: 'Order it',
      noMatchMessage: 'Pick another',
    });
    expect(card['notice']()).toBe('Spoolman settings saved.');
    expect(card['saving']()).toBe(false);
    expect(saved).toEqual([NO_SETTINGS]);

    card['form'].controls.tolerancePreset.setValue('custom');
    card['onPresetChanged']();
    expect(card['customTolerance']()).toBe(true);
    card['form'].controls.customTolerance.setValue(' 42 ');
    card['save']();
    expect(settingsService.updateSpoolman).toHaveBeenLastCalledWith(
      expect.objectContaining({ matchTolerance: 42 }),
    );
  });

  it.each(['0', '101', '2.5', 'abc', ''])('refuses the custom tolerance %s', (custom) => {
    const card = create();
    card['form'].controls.tolerancePreset.setValue('custom');
    card['form'].controls.customTolerance.setValue(custom);

    card['save']();

    expect(card['error']()).toBe('The tolerance must be a whole number from 1 to 100');
    expect(settingsService.updateSpoolman).not.toHaveBeenCalled();
  });

  it('refuses warning texts that are too long', () => {
    const card = create();
    card['form'].controls.noMatchMessage.setValue('x'.repeat(301));

    card['save']();

    expect(card['error']()).toBe('The warning texts must be at most 300 characters');
    expect(settingsService.updateSpoolman).not.toHaveBeenCalled();
  });

  it('shows why saving failed', () => {
    settingsService.updateSpoolman.mockReturnValue(
      throwError(() => {
        return new HttpErrorResponse({
          status: 400,
          error: { message: 'Switch colour selection on before automatic matching' },
        });
      }),
    );
    const card = create();

    card['save']();

    expect(card['error']()).toBe('Switch colour selection on before automatic matching');
    expect(card['saving']()).toBe(false);
    expect(saved).toEqual([]);
  });
});
