import { Component, computed, inject, input, OnInit, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
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

/**
 * Spoolman (#11): whether it is set up and reachable, the two switches and the warning requesters
 * see when a chosen filament runs short. Colour selection is part of the integration, so it can
 * only be on while the integration is.
 */
@Component({
  selector: 'app-spoolman-settings',
  imports: [ReactiveFormsModule],
  templateUrl: './spoolman-settings.html',
})
export class SpoolmanSettingsCard implements OnInit {
  private readonly settingsService = inject(SettingsService);
  private readonly fb = inject(FormBuilder);

  readonly settings = input.required<SpoolmanSettings>();
  /** Null while it is being checked. */
  readonly status = input<SpoolmanStatus | null>(null);
  readonly saved = output<Settings>();

  protected readonly form = this.fb.nonNullable.group({
    enabled: [false],
    colorSelection: [false],
    notEnoughMessage: ['', [Validators.maxLength(MAX_MESSAGE_LENGTH)]],
  });
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
      notEnoughMessage: settings.notEnoughMessage,
    });
    this.integrationOn.set(settings.enabled);
  }

  /** Switching the integration off switches colour selection off too. */
  protected onIntegrationToggled(): void {
    const enabled = this.form.controls.enabled.value;
    this.integrationOn.set(enabled);
    if (!enabled) {
      this.form.controls.colorSelection.setValue(false);
    }
  }

  protected save(): void {
    this.notice.set(null);
    this.error.set(null);
    if (this.form.controls.notEnoughMessage.invalid) {
      this.error.set(`The warning text must be at most ${MAX_MESSAGE_LENGTH} characters`);
      return;
    }
    this.saving.set(true);
    this.settingsService.updateSpoolman(this.form.getRawValue()).subscribe({
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
