import { Component, inject, OnInit, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { errorMessage } from '../../core/http/api-error';
import { Settings } from '../../core/models/settings.model';
import { SettingsService } from './settings.service';
import { SlicingSettingsCard, SuggestedBed } from './slicing-settings/slicing-settings';

/** Same limits as the backend (whole millimetres, 1–2000). */
export const MIN_PLATE_SIZE = 1;
export const MAX_PLATE_SIZE = 2000;
const PLATE_SIZE_VALIDATORS = [
  Validators.required,
  Validators.min(MIN_PLATE_SIZE),
  Validators.max(MAX_PLATE_SIZE),
  Validators.pattern(/^\d+$/),
];

/** Admin page for application settings: the build plate and server slicing. */
@Component({
  selector: 'app-settings-page',
  imports: [ReactiveFormsModule, SlicingSettingsCard],
  templateUrl: './settings-page.html',
})
export class SettingsPage implements OnInit {
  private readonly settingsService = inject(SettingsService);
  private readonly fb = inject(FormBuilder);

  protected readonly form = this.fb.nonNullable.group({
    x: ['', PLATE_SIZE_VALIDATORS],
    y: ['', PLATE_SIZE_VALIDATORS],
    z: ['', PLATE_SIZE_VALIDATORS],
  });
  protected readonly axes: { key: 'x' | 'y' | 'z'; label: string }[] = [
    { key: 'x', label: 'Width (X, mm)' },
    { key: 'y', label: 'Depth (Y, mm)' },
    { key: 'z', label: 'Height (Z, mm)' },
  ];
  protected readonly settings = signal<Settings | null>(null);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  /** Set when a printer filled in the plate size that isn't saved yet. */
  protected readonly filledFrom = signal<string | null>(null);

  ngOnInit(): void {
    this.settingsService.get().subscribe({
      next: (settings) => {
        this.fill(settings);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(errorMessage(err, 'Could not load the settings.'));
        this.loading.set(false);
      },
    });
  }

  protected save(): void {
    this.form.markAllAsTouched();
    this.notice.set(null);
    if (this.form.invalid) {
      return;
    }
    const { x, y, z } = this.form.getRawValue();
    this.saving.set(true);
    this.error.set(null);
    this.settingsService.updateBuildPlate({ x: Number(x), y: Number(y), z: Number(z) }).subscribe({
      next: (settings) => {
        this.fill(settings);
        this.saving.set(false);
        this.notice.set('Build plate saved.');
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(errorMessage(err, 'Could not save the build plate.'));
      },
    });
  }

  /** A printer was chosen for slicing: offer its build volume (saved with the build plate's Save). */
  protected onBedSuggested(bed: SuggestedBed): void {
    this.form.setValue({ x: String(bed.x), y: String(bed.y), z: String(bed.z) });
    this.form.markAsDirty();
    this.filledFrom.set(bed.printer);
  }

  protected onSlicingSaved(settings: Settings): void {
    this.settings.set(settings);
  }

  /** User-facing text for the first error on a size field, once touched. */
  protected sizeError(control: AbstractControl): string | null {
    if (!control.errors || !(control.touched || control.dirty)) {
      return null;
    }
    if (control.errors['required']) {
      return 'Required';
    }
    return `A whole number from ${MIN_PLATE_SIZE} to ${MAX_PLATE_SIZE}`;
  }

  private fill(settings: Settings): void {
    this.settings.set(settings);
    this.form.setValue({
      x: sizeText(settings.buildPlate.x),
      y: sizeText(settings.buildPlate.y),
      z: sizeText(settings.buildPlate.z),
    });
    this.form.markAsPristine();
    this.form.markAsUntouched();
    this.filledFrom.set(null);
  }
}

function sizeText(size: number | null): string {
  if (size === null) {
    return '';
  }
  return String(size);
}
