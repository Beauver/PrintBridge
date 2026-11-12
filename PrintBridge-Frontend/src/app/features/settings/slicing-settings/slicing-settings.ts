import { Component, computed, inject, input, OnInit, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { errorMessage } from '../../../core/http/api-error';
import { Settings, SlicingSettings } from '../../../core/models/settings.model';
import { SlicerPrinter, SlicerProfiles, SlicerStatus } from '../../../core/models/slicer.model';
import { formatNozzle } from '../../../shared/model-info-display';
import { SettingsService } from '../settings.service';

/** Same limits as the backend. */
export const MIN_TIMEOUT_MINUTES = 1;
export const MAX_TIMEOUT_MINUTES = 60;
/** Preselected when a printer has it: the most common nozzle. */
const PREFERRED_NOZZLE = 0.4;

/** A printer's build volume, offered to the build plate form. */
export interface SuggestedBed {
  printer: string;
  x: number;
  y: number;
  z: number;
}

/**
 * Server slicing settings (#15): on/off, timeout, the printer with its owned nozzles, and the
 * process/filament profiles for the default nozzle. Lists come from the slicer service.
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
  readonly saved = output<Settings>();
  readonly bedSuggested = output<SuggestedBed>();

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
    printer: [''],
    process: [''],
    filament: [''],
  });
  /** Kept outside the form: they are checkboxes and a select built from the printer's nozzles. */
  protected readonly ownedNozzles = signal<number[]>([]);
  protected readonly defaultNozzle = signal<number | null>(null);
  protected readonly printerModel = signal('');

  protected readonly status = signal<SlicerStatus | null>(null);
  protected readonly printers = signal<SlicerPrinter[]>([]);
  protected readonly profiles = signal<SlicerProfiles | null>(null);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly selectedPrinter = computed(() => {
    return (
      this.printers().find((printer) => {
        return printer.model === this.printerModel();
      }) ?? null
    );
  });

  /** The printer's nozzle sizes; without the slicer only the saved ones are known. */
  protected readonly nozzleOptions = computed(() => {
    const printer = this.selectedPrinter();
    if (printer) {
      return printer.nozzles.map((nozzle) => {
        return nozzle.diameter;
      });
    }
    return this.ownedNozzles();
  });

  /** The saved printer stays selectable even when the slicer can't list it right now. */
  protected readonly printerOptions = computed(() => {
    const models = this.printers().map((printer) => {
      return printer.model;
    });
    const saved = this.settings().printer;
    if (saved && !models.includes(saved)) {
      models.unshift(saved);
    }
    return models;
  });

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

  protected readonly formatNozzle = formatNozzle;

  ngOnInit(): void {
    const settings = this.settings();
    this.form.setValue({
      enabled: settings.enabled,
      timeoutMinutes: String(settings.timeoutMinutes),
      printer: settings.printer ?? '',
      process: settings.process ?? '',
      filament: settings.filament ?? '',
    });
    this.printerModel.set(settings.printer ?? '');
    this.ownedNozzles.set([...settings.ownedNozzles]);
    this.defaultNozzle.set(settings.defaultNozzle);

    this.settingsService.slicerStatus().subscribe({
      next: (status) => {
        this.status.set(status);
        if (status.reachable) {
          this.loadPrinters();
        }
      },
      error: () => {
        this.status.set({
          configured: false,
          reachable: false,
          orcaVersion: null,
          message: 'Could not check the slicer',
        });
      },
    });
  }

  /** A new printer: preselect its 0.4 mm nozzle (or the first), and offer its build volume. */
  protected onPrinterChange(): void {
    const model = this.form.controls.printer.value;
    this.printerModel.set(model);
    this.form.patchValue({ process: '', filament: '' });
    this.profiles.set(null);
    const printer = this.selectedPrinter();
    if (!printer || printer.nozzles.length === 0) {
      this.ownedNozzles.set([]);
      this.defaultNozzle.set(null);
      return;
    }
    const preferred =
      printer.nozzles.find((nozzle) => {
        return nozzle.diameter === PREFERRED_NOZZLE;
      }) ?? printer.nozzles[0];
    this.ownedNozzles.set([preferred.diameter]);
    this.defaultNozzle.set(preferred.diameter);
    this.bedSuggested.emit({ printer: printer.model, ...preferred.bed });
    this.loadProfiles();
  }

  protected isOwned(diameter: number): boolean {
    return this.ownedNozzles().includes(diameter);
  }

  protected toggleNozzle(diameter: number, owned: boolean): void {
    let nozzles = this.ownedNozzles().filter((nozzle) => {
      return nozzle !== diameter;
    });
    if (owned) {
      nozzles = [...nozzles, diameter].sort((a, b) => {
        return a - b;
      });
    }
    this.ownedNozzles.set(nozzles);
    const currentDefault = this.defaultNozzle();
    if (currentDefault === null || !nozzles.includes(currentDefault)) {
      this.changeDefaultNozzle(nozzles[0] ?? null);
    }
  }

  protected onDefaultNozzleChange(value: string): void {
    this.changeDefaultNozzle(Number(value));
  }

  protected save(): void {
    this.form.markAllAsTouched();
    this.notice.set(null);
    this.error.set(null);
    const invalid = this.validationError();
    if (invalid) {
      this.error.set(invalid);
      return;
    }
    const value = this.form.getRawValue();
    const slicing: SlicingSettings = {
      enabled: value.enabled,
      timeoutMinutes: Number(value.timeoutMinutes),
      printer: blankToNull(value.printer),
      ownedNozzles: this.ownedNozzles(),
      defaultNozzle: this.defaultNozzle(),
      process: blankToNull(value.process),
      filament: blankToNull(value.filament),
    };
    this.saving.set(true);
    this.settingsService.updateSlicing(slicing).subscribe({
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

  /** The same rules as the backend, so the admin sees them before saving. */
  private validationError(): string | null {
    if (this.form.controls.timeoutMinutes.invalid) {
      return `The timeout must be a whole number of minutes from ${MIN_TIMEOUT_MINUTES} to ${MAX_TIMEOUT_MINUTES}`;
    }
    const value = this.form.getRawValue();
    const complete =
      value.printer !== '' &&
      this.ownedNozzles().length > 0 &&
      this.defaultNozzle() !== null &&
      value.process !== '' &&
      value.filament !== '';
    if (value.enabled && !complete) {
      return 'Choose a printer, nozzles and profiles before switching slicing on';
    }
    const filaments = this.profiles()?.filaments;
    if (value.filament !== '' && filaments && !filaments.includes(value.filament)) {
      return 'Choose a filament profile from the list';
    }
    return null;
  }

  private changeDefaultNozzle(nozzle: number | null): void {
    this.defaultNozzle.set(nozzle);
    this.form.patchValue({ process: '', filament: '' });
    this.profiles.set(null);
    this.loadProfiles();
  }

  private loadPrinters(): void {
    this.settingsService.printers().subscribe({
      next: (printers) => {
        this.printers.set(printers);
        this.loadProfiles();
      },
      error: (err: unknown) => {
        this.error.set(errorMessage(err, 'Could not load the printers from the slicer.'));
      },
    });
  }

  private loadProfiles(): void {
    const printer = this.printerModel();
    const nozzle = this.defaultNozzle();
    if (printer === '' || nozzle === null || this.printers().length === 0) {
      return;
    }
    this.settingsService.profiles(printer, nozzle).subscribe({
      next: (profiles) => {
        this.profiles.set(profiles);
      },
      error: (err: unknown) => {
        this.error.set(errorMessage(err, 'Could not load the profiles from the slicer.'));
      },
    });
  }
}

function blankToNull(value: string): string | null {
  if (value.trim() === '') {
    return null;
  }
  return value.trim();
}
