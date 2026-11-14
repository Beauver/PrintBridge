import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { Settings, UploadLimits } from '../../../core/models/settings.model';
import { NO_SETTINGS } from '../../../core/testing';
import { SettingsService } from '../settings.service';
import { UploadSettingsCard } from './upload-settings';

describe('UploadSettingsCard', () => {
  const settingsService = { updateUploads: vi.fn() };
  const LIMITS: UploadLimits = { maxFiles: 10, maxFileSizeMb: 100, ceilingMb: 200 };
  let saved: Settings[];

  function create(): UploadSettingsCard {
    const fixture = TestBed.createComponent(UploadSettingsCard);
    fixture.componentRef.setInput('settings', LIMITS);
    const card = fixture.componentInstance;
    card.saved.subscribe((settings) => {
      saved.push(settings);
    });
    card.ngOnInit();
    return card;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    saved = [];
    TestBed.configureTestingModule({
      providers: [{ provide: SettingsService, useValue: settingsService }],
    });
  });

  it('fills the form from the settings', () => {
    expect(create()['form'].getRawValue()).toEqual({ maxFiles: '10', maxFileSizeMb: '100' });
  });

  it('saves the limits', () => {
    settingsService.updateUploads.mockReturnValue(of(NO_SETTINGS));
    const card = create();
    card['form'].setValue({ maxFiles: '50', maxFileSizeMb: '200' });

    card['save']();

    expect(settingsService.updateUploads).toHaveBeenCalledWith({
      maxFiles: 50,
      maxFileSizeMb: 200,
    });
    expect(card['notice']()).toBe('Upload settings saved.');
    expect(card['saving']()).toBe(false);
    expect(saved).toEqual([NO_SETTINGS]);
  });

  it.each([
    ['0', '100'],
    ['51', '100'],
    ['2.5', '100'],
    ['', '100'],
  ])('refuses %s files', (maxFiles, maxFileSizeMb) => {
    const card = create();
    card['form'].setValue({ maxFiles, maxFileSizeMb });
    card['save']();
    expect(card['error']()).toBe('Max files must be a whole number from 1 to 50');
    expect(settingsService.updateUploads).not.toHaveBeenCalled();
  });

  it.each(['0', '201', 'abc'])('refuses %s MB', (maxFileSizeMb) => {
    const card = create();
    card['form'].setValue({ maxFiles: '10', maxFileSizeMb });
    card['save']();
    expect(card['error']()).toBe('Max size per file must be a whole number of MB from 1 to 200');
    expect(settingsService.updateUploads).not.toHaveBeenCalled();
  });

  it('shows a save error', () => {
    settingsService.updateUploads.mockReturnValue(
      throwError(() => {
        return new HttpErrorResponse({ status: 400, error: { message: 'Too big' } });
      }),
    );
    const card = create();
    card['save']();
    expect(card['error']()).toBe('Too big');
    expect(card['saving']()).toBe(false);
    expect(saved).toEqual([]);
  });
});
