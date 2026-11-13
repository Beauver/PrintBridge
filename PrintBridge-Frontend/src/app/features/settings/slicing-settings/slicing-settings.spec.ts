import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { SlicingSettings } from '../../../core/models/settings.model';
import { SlicerStatus } from '../../../core/models/slicer.model';
import { SettingsService } from '../settings.service';
import { SlicingSettingsCard } from './slicing-settings';

describe('SlicingSettingsCard', () => {
  const settingsService = { updateSlicing: vi.fn() };
  const OFF: SlicingSettings = { enabled: false, timeoutMinutes: 10 };

  function create(settings: SlicingSettings = OFF, status: SlicerStatus | null = null) {
    const fixture = TestBed.createComponent(SlicingSettingsCard);
    fixture.componentRef.setInput('settings', settings);
    fixture.componentRef.setInput('status', status);
    const card = fixture.componentInstance;
    card.ngOnInit();
    return card;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [{ provide: SettingsService, useValue: settingsService }],
    });
  });

  it('describes the slicer status', () => {
    expect(create()['statusText']()).toBeNull();
    expect(
      create(OFF, { configured: true, reachable: true, orcaVersion: '2.5.0', message: null })[
        'statusText'
      ](),
    ).toEqual({ ok: true, text: 'Slicer reachable · OrcaSlicer 2.5.0' });
    expect(
      create(OFF, { configured: true, reachable: false, orcaVersion: null, message: 'Down' })[
        'statusText'
      ](),
    ).toEqual({ ok: false, text: 'Down' });
    expect(
      create(OFF, { configured: false, reachable: false, orcaVersion: null, message: null })[
        'statusText'
      ](),
    ).toEqual({ ok: false, text: 'The slicer is not reachable' });
  });

  it('credits OrcaSlicer with a link to the source of the version in use', () => {
    expect(create()['orcaSlicer']()).toBeNull();
    expect(
      create(OFF, { configured: true, reachable: false, orcaVersion: null, message: 'Down' })[
        'orcaSlicer'
      ](),
    ).toBeNull();
    expect(
      create(OFF, { configured: true, reachable: true, orcaVersion: '2.5.0', message: null })[
        'orcaSlicer'
      ](),
    ).toEqual({
      version: '2.5.0',
      sourceUrl: 'https://github.com/OrcaSlicer/OrcaSlicer/tree/v2.5.0',
    });
  });

  it('saves on/off and the timeout', () => {
    const card = create({ enabled: true, timeoutMinutes: 20 });
    const saved = vi.fn();
    card.saved.subscribe(saved);
    const response = { slicing: { enabled: false, timeoutMinutes: 30 } };
    settingsService.updateSlicing.mockReturnValue(of(response));

    card['form'].setValue({ enabled: false, timeoutMinutes: '30' });
    card['save']();

    expect(settingsService.updateSlicing).toHaveBeenCalledWith({
      enabled: false,
      timeoutMinutes: 30,
    });
    expect(saved).toHaveBeenCalledWith(response);
    expect(card['notice']()).toBe('Slicing settings saved.');
  });

  it('checks the timeout before saving', () => {
    const card = create();
    card['form'].controls.timeoutMinutes.setValue('61');

    card['save']();

    expect(card['error']()).toBe('The timeout must be a whole number of minutes from 1 to 60');
    expect(settingsService.updateSlicing).not.toHaveBeenCalled();
  });

  it('shows why the backend refused it', () => {
    const card = create();
    settingsService.updateSlicing.mockReturnValue(
      throwError(() => {
        return new HttpErrorResponse({
          status: 400,
          error: { message: 'Add a printer before switching slicing on' },
        });
      }),
    );

    card['save']();

    expect(card['error']()).toBe('Add a printer before switching slicing on');
    expect(card['saving']()).toBe(false);
  });
});
