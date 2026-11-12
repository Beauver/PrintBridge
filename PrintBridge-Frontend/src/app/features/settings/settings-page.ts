import { Component, inject, OnInit, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { errorMessage } from '../../core/http/api-error';
import { Settings } from '../../core/models/settings.model';
import { SettingsService } from './settings.service';

/** Same limits as the backend (whole millimetres, 1–2000). */
export const MIN_PLATE_SIZE = 1;
export const MAX_PLATE_SIZE = 2000;
const PLATE_SIZE_VALIDATORS = [
  Validators.required,
  Validators.min(MIN_PLATE_SIZE),
  Validators.max(MAX_PLATE_SIZE),
  Validators.pattern(/^\d+$/),
];

/** Admin page for application settings: for now the printer's build plate size. */
@Component({
  selector: 'app-settings-page',
  imports: [ReactiveFormsModule],
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
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

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
    this.settingsService
      .update({ buildPlateX: Number(x), buildPlateY: Number(y), buildPlateZ: Number(z) })
      .subscribe({
        next: (settings) => {
          this.fill(settings);
          this.saving.set(false);
          this.notice.set('Settings saved.');
        },
        error: (err: unknown) => {
          this.saving.set(false);
          this.error.set(errorMessage(err, 'Could not save the settings.'));
        },
      });
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
    this.form.setValue({
      x: sizeText(settings.buildPlateX),
      y: sizeText(settings.buildPlateY),
      z: sizeText(settings.buildPlateZ),
    });
    this.form.markAsPristine();
    this.form.markAsUntouched();
  }
}

function sizeText(size: number | null): string {
  if (size === null) {
    return '';
  }
  return String(size);
}
