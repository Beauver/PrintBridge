import { Component, computed, inject, input, OnInit, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { errorMessage } from '../../../core/http/api-error';
import { Settings, SlicingSettings } from '../../../core/models/settings.model';
import { SlicerStatus } from '../../../core/models/slicer.model';
import { SettingsService } from '../settings.service';

/** Same limits as the backend. */
export const MIN_TIMEOUT_MINUTES = 1;
export const MAX_TIMEOUT_MINUTES = 60;

/**
 * Server slicing (#15): on/off, the timeout per file and whether the slicer service is reachable.
 * The printers it slices for are set up in the printers card.
 */
@Component({
  selector: 'app-slicing-settings',
  imports: [ReactiveFormsModule],
  templateUrl: './slicing-settings.html',
})
export class SlicingSettingsCard implements OnInit {
  private readonly settingsService = inject(SettingsService);
  private readonly fb = inject(FormBuilder);

  readonly settings = input.required<SlicingSettings>();
  /** Null while it is being checked. */
  readonly status = input<SlicerStatus | null>(null);
  readonly saved = output<Settings>();

  protected readonly form = this.fb.nonNullable.group({
    enabled: [false],
    timeoutMinutes: [
      '10',
      [
        Validators.required,
        Validators.min(MIN_TIMEOUT_MINUTES),
        Validators.max(MAX_TIMEOUT_MINUTES),
        Validators.pattern(/^\d+$/),
      ],
    ],
  });
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly statusText = computed(() => {
    const status = this.status();
    if (!status) {
      return null;
    }
    if (status.reachable) {
      return { ok: true, text: `Slicer reachable · OrcaSlicer ${status.orcaVersion}` };
    }
    return { ok: false, text: status.message ?? 'The slicer is not reachable' };
  });

  ngOnInit(): void {
    const settings = this.settings();
    this.form.setValue({
      enabled: settings.enabled,
      timeoutMinutes: String(settings.timeoutMinutes),
    });
  }

  protected save(): void {
    this.form.markAllAsTouched();
    this.notice.set(null);
    this.error.set(null);
    if (this.form.controls.timeoutMinutes.invalid) {
      this.error.set(
        `The timeout must be a whole number of minutes from ${MIN_TIMEOUT_MINUTES} to ${MAX_TIMEOUT_MINUTES}`,
      );
      return;
    }
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.settingsService
      .updateSlicing({ enabled: value.enabled, timeoutMinutes: Number(value.timeoutMinutes) })
      .subscribe({
        next: (settings) => {
          this.saving.set(false);
          this.notice.set('Slicing settings saved.');
          this.saved.emit(settings);
        },
        error: (err: unknown) => {
          this.saving.set(false);
          this.error.set(errorMessage(err, 'Could not save the slicing settings.'));
        },
      });
  }
}
