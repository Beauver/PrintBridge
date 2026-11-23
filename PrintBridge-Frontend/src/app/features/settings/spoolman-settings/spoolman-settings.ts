import { Component, computed, inject, input, OnInit, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../../core/http/api-error';
import { Settings, SpoolmanSettings } from '../../../core/models/settings.model';
import { SpoolmanStatus } from '../../../core/models/spoolman.model';
import { SettingsService } from '../settings.service';

/** The status line under the title. */
interface StatusText {
  text: string;
  ok: boolean;
}

/** Same limit as the backend. */
export const MAX_MESSAGE_LENGTH = 300;

/** How close automatic matching wants a colour (#76): CIEDE2000 ΔE per preset; else a custom number. */
export type TolerancePreset = 'strict' | 'normal' | 'loose' | 'custom';
export const TOLERANCE_PRESETS: { id: TolerancePreset; label: string; deltaE: number | null }[] = [
  { id: 'strict', label: 'Strict (ΔE 10): only very close shades', deltaE: 10 },
  { id: 'normal', label: 'Normal (ΔE 20): the same colour family', deltaE: 20 },
  { id: 'loose', label: 'Loose (ΔE 35): roughly the same colour', deltaE: 35 },
  { id: 'custom', label: 'Custom', deltaE: null },
];
export const MIN_TOLERANCE = 1;
export const MAX_TOLERANCE = 100;

/** The preset a saved tolerance belongs to; any other number is custom. */
export function presetOf(tolerance: number): TolerancePreset {
  const preset = TOLERANCE_PRESETS.find((candidate) => {
    return candidate.deltaE === tolerance;
  });
  return preset?.id ?? 'custom';
}

/**
 * Spoolman (#11): whether it is set up and reachable, the switches and the warning requesters see
 * when a chosen filament runs short. Colour selection and Spoolman prices for the estimate (#72) are
 * part of the integration, so they can only be on while the integration is.
 */
@Component({
  selector: 'app-spoolman-settings',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './spoolman-settings.html',
})
export class SpoolmanSettingsCard implements OnInit {
  private readonly settingsService = inject(SettingsService);
  private readonly fb = inject(FormBuilder);

  readonly settings = input.required<SpoolmanSettings>();
  /** Null while it is being checked. */
  readonly status = input<SpoolmanStatus | null>(null);
  /** ISO 4217 code that Spoolman's prices are taken to be in (#75). */
  readonly currency = input('EUR');
  readonly saved = output<Settings>();

  protected readonly form = this.fb.nonNullable.group({
    enabled: [false],
    colorSelection: [false],
    prices: [false],
    autoMatch: [false],
    tolerancePreset: ['normal' as TolerancePreset],
    customTolerance: ['20'],
    notEnoughMessage: ['', [Validators.maxLength(MAX_MESSAGE_LENGTH)]],
    noMatchMessage: ['', [Validators.maxLength(MAX_MESSAGE_LENGTH)]],
  });
  protected readonly presets = TOLERANCE_PRESETS;
  /** Mirrors the colour selection switch, so automatic matching can follow it. */
  protected readonly selectionOn = signal(false);
  /** Mirrors the tolerance select, so the custom field shows. */
  protected readonly customTolerance = signal(false);
  protected readonly maxMessageLength = MAX_MESSAGE_LENGTH;
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  /** Mirrors the integration switch, so colour selection can follow it. */
  protected readonly integrationOn = signal(false);

  /** Without SPOOLMAN_URL nothing can be switched on (switching off still works). */
  protected readonly configured = computed(() => {
    const status = this.status();
    if (status === null) {
      return true;
    }
    return status.configured;
  });

  protected readonly statusText = computed<StatusText | null>(() => {
    const status = this.status();
    if (status === null) {
      return null;
    }
    if (!status.configured) {
      return { text: 'Not configured: set SPOOLMAN_URL on the server.', ok: false };
    }
    if (!status.reachable) {
      return { text: `Can't reach Spoolman at ${status.url}: ${status.message}`, ok: false };
    }
    return { text: `Connected to Spoolman ${status.version} at ${status.url}.`, ok: true };
  });

  ngOnInit(): void {
    const settings = this.settings();
    this.form.setValue({
      enabled: settings.enabled,
      colorSelection: settings.colorSelection,
      prices: settings.prices,
      autoMatch: settings.autoMatch,
      tolerancePreset: presetOf(settings.matchTolerance),
      customTolerance: String(settings.matchTolerance),
      notEnoughMessage: settings.notEnoughMessage,
      noMatchMessage: settings.noMatchMessage,
    });
    this.integrationOn.set(settings.enabled);
    this.selectionOn.set(settings.colorSelection);
    this.customTolerance.set(presetOf(settings.matchTolerance) === 'custom');
  }

  /** Switching the integration off switches everything that needs it off too. */
  protected onIntegrationToggled(): void {
    const enabled = this.form.controls.enabled.value;
    this.integrationOn.set(enabled);
    if (!enabled) {
      this.form.controls.colorSelection.setValue(false);
      this.form.controls.prices.setValue(false);
      this.onColorSelectionToggled();
    }
  }

  /** Automatic matching makes choices people change afterwards, so it needs colour selection. */
  protected onColorSelectionToggled(): void {
    const on = this.form.controls.colorSelection.value;
    this.selectionOn.set(on);
    if (!on) {
      this.form.controls.autoMatch.setValue(false);
    }
  }

  protected onPresetChanged(): void {
    this.customTolerance.set(this.form.controls.tolerancePreset.value === 'custom');
  }

  /** The tolerance to save: the preset's ΔE, or the custom number; null when the custom one is invalid. */
  private tolerance(): number | null {
    const value = this.form.getRawValue();
    const preset = TOLERANCE_PRESETS.find((candidate) => {
      return candidate.id === value.tolerancePreset;
    });
    if (preset?.deltaE) {
      return preset.deltaE;
    }
    const custom = Number(value.customTolerance);
    if (
      !/^\d+$/.test(value.customTolerance.trim()) ||
      custom < MIN_TOLERANCE ||
      custom > MAX_TOLERANCE
    ) {
      return null;
    }
    return custom;
  }

  protected save(): void {
    this.notice.set(null);
    this.error.set(null);
    if (this.form.controls.notEnoughMessage.invalid || this.form.controls.noMatchMessage.invalid) {
      this.error.set(`The warning texts must be at most ${MAX_MESSAGE_LENGTH} characters`);
      return;
    }
    const matchTolerance = this.tolerance();
    if (matchTolerance === null) {
      this.error.set(
        `The tolerance must be a whole number from ${MIN_TOLERANCE} to ${MAX_TOLERANCE}`,
      );
      return;
    }
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.settingsService
      .updateSpoolman({
        enabled: value.enabled,
        colorSelection: value.colorSelection,
        prices: value.prices,
        autoMatch: value.autoMatch,
        matchTolerance,
        notEnoughMessage: value.notEnoughMessage,
        noMatchMessage: value.noMatchMessage,
      })
      .subscribe({
        next: (settings) => {
          this.saving.set(false);
          this.notice.set('Spoolman settings saved.');
          this.saved.emit(settings);
        },
        error: (err: unknown) => {
          this.saving.set(false);
          this.error.set(errorMessage(err, 'Could not save the Spoolman settings.'));
        },
      });
  }
}
