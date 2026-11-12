import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { Settings } from '../../core/models/settings.model';
import { SettingsPage } from './settings-page';
import { SettingsService } from './settings.service';

describe('SettingsPage', () => {
  const settingsService = { get: vi.fn(), update: vi.fn() };
  const EMPTY: Settings = { buildPlateX: null, buildPlateY: null, buildPlateZ: null };
  const SAVED: Settings = { buildPlateX: 256, buildPlateY: 256, buildPlateZ: 250 };

  function serverError() {
    return throwError(() => {
      return new HttpErrorResponse({ status: 500 });
    });
  }

  function create(settings: Settings = EMPTY): SettingsPage {
    settingsService.get.mockReturnValue(of(settings));
    const page = TestBed.createComponent(SettingsPage).componentInstance;
    page.ngOnInit();
    return page;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [{ provide: SettingsService, useValue: settingsService }],
    });
  });

  it('starts empty when no plate size is set', () => {
    const page = create();

    expect(page['loading']()).toBe(false);
    expect(page['form'].getRawValue()).toEqual({ x: '', y: '', z: '' });
  });

  it('shows the saved plate size', () => {
    expect(create(SAVED)['form'].getRawValue()).toEqual({ x: '256', y: '256', z: '250' });
  });

  it('shows a load error', () => {
    settingsService.get.mockReturnValue(serverError());
    const page = TestBed.createComponent(SettingsPage).componentInstance;
    page.ngOnInit();

    expect(page['error']()).toBe('Could not load the settings.');
    expect(page['loading']()).toBe(false);
  });

  it('saves whole millimetres', () => {
    const page = create();
    settingsService.update.mockReturnValue(of(SAVED));
    page['form'].setValue({ x: '256', y: '256', z: '250' });

    page['save']();

    expect(settingsService.update).toHaveBeenCalledWith(SAVED);
    expect(page['notice']()).toBe('Settings saved.');
    expect(page['saving']()).toBe(false);
  });

  it('does not save invalid sizes', () => {
    const page = create();
    page['form'].setValue({ x: '', y: '2001', z: '1.5' });

    page['save']();

    expect(settingsService.update).not.toHaveBeenCalled();
    const controls = page['form'].controls;
    expect(page['sizeError'](controls.x)).toBe('Required');
    expect(page['sizeError'](controls.y)).toBe('A whole number from 1 to 2000');
    expect(page['sizeError'](controls.z)).toBe('A whole number from 1 to 2000');
  });

  it('shows no error on untouched or valid fields', () => {
    const page = create(SAVED);

    expect(page['sizeError'](page['form'].controls.x)).toBeNull();
    page['form'].controls.x.markAsTouched();
    expect(page['sizeError'](page['form'].controls.x)).toBeNull();
  });

  it('shows a save error', () => {
    const page = create(SAVED);
    settingsService.update.mockReturnValue(serverError());

    page['save']();

    expect(page['error']()).toBe('Could not save the settings.');
    expect(page['saving']()).toBe(false);
    expect(page['notice']()).toBeNull();
  });
});
