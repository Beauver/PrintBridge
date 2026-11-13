import { Component, computed, inject, input, OnInit, output, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { errorMessage } from '../../../core/http/api-error';
import { Printer, PrinterInput, Settings } from '../../../core/models/settings.model';
import { SlicerPrinter, SlicerProfiles } from '../../../core/models/slicer.model';
import { formatNozzle } from '../../../shared/model-info-display';
import { SettingsService } from '../settings.service';

/** Same limits as the backend (whole millimetres, 1–2000). */
export const MIN_PLATE_SIZE = 1;
export const MAX_PLATE_SIZE = 2000;
const PLATE_SIZE_VALIDATORS = [
  Validators.required,
  Validators.min(MIN_PLATE_SIZE),
  Validators.max(MAX_PLATE_SIZE),
  Validators.pattern(/^\d+$/),
];
/** Preselected when a printer has it: the most common nozzle. */
const PREFERRED_NOZZLE = 0.4;

/**
 * Adds or changes one printer: its model, build plate and, for server slicing, the nozzles the
 * admin owns and the profiles for the default nozzle. Models and profiles come from the slicer
 * service; without it the model is typed in and only the build plate matters.
 */
@Component({
  selector: 'app-printer-form',
  imports: [ReactiveFormsModule],
  templateUrl: './printer-form.html',
})
export class PrinterForm implements OnInit {
  private readonly settingsService = inject(SettingsService);
  private readonly fb = inject(FormBuilder);

  /** The printer to change; null adds a new one. */
  readonly printer = input<Printer | null>(null);
  /** What the slicer offers; empty when it can't be reached. */
  readonly slicerPrinters = input<SlicerPrinter[]>([]);
  /** While slicing is on, nozzles and profiles are required. */
  readonly slicingEnabled = input(false);
  readonly saved = output<Settings>();
  readonly cancelled = output<void>();

  protected readonly form = this.fb.nonNullable.group({
    model: ['', [Validators.required, Validators.maxLength(200)]],
    x: ['', PLATE_SIZE_VALIDATORS],
    y: ['', PLATE_SIZE_VALIDATORS],
    z: ['', PLATE_SIZE_VALIDATORS],
    process: [''],
    filament: [''],
  });
  protected readonly axes: { key: 'x' | 'y' | 'z'; label: string }[] = [
    { key: 'x', label: 'Width (X, mm)' },
    { key: 'y', label: 'Depth (Y, mm)' },
    { key: 'z', label: 'Height (Z, mm)' },
  ];
  /** Kept outside the form: they are checkboxes and a select built from the printer's nozzles. */
  protected readonly ownedNozzles = signal<number[]>([]);
  protected readonly defaultNozzle = signal<number | null>(null);
  protected readonly model = signal('');
  protected readonly profiles = signal<SlicerProfiles | null>(null);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Set when the build plate was filled in from the slicer's printer. */
  protected readonly plateFilled = signal(false);

  protected readonly isNew = computed(() => {
    return this.printer() === null;
  });

  /** Pick the model from the slicer's list when it is reachable, else type it. */
  protected readonly chooseModel = computed(() => {
    return this.slicerPrinters().length > 0;
  });

  protected readonly selectedPrinter = computed(() => {
    return (
      this.slicerPrinters().find((printer) => {
        return printer.model === this.model();
      }) ?? null
    );
  });

  /** The saved model stays selectable even when the slicer doesn't list it. */
  protected readonly modelOptions = computed(() => {
    const models = this.slicerPrinters().map((printer) => {
      return printer.model;
    });
    const saved = this.printer()?.model;
    if (saved && !models.includes(saved)) {
      models.unshift(saved);
    }
    return models;
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

  protected readonly formatNozzle = formatNozzle;

  ngOnInit(): void {
    const printer = this.printer();
    if (!printer) {
      return;
    }
    this.form.setValue({
      model: printer.model,
      x: String(printer.buildPlate.x),
      y: String(printer.buildPlate.y),
      z: String(printer.buildPlate.z),
      process: printer.process ?? '',
      filament: printer.filament ?? '',
    });
    this.model.set(printer.model);
    this.ownedNozzles.set([...printer.ownedNozzles]);
    this.defaultNozzle.set(printer.defaultNozzle);
    this.loadProfiles();
  }

  /** Another model: preselect its 0.4 mm nozzle (or the first) and fill in its build volume. */
  protected onModelChange(): void {
    this.model.set(this.form.controls.model.value.trim());
    this.form.patchValue({ process: '', filament: '' });
    this.profiles.set(null);
    this.plateFilled.set(false);
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
    this.form.patchValue({
      x: String(preferred.bed.x),
      y: String(preferred.bed.y),
      z: String(preferred.bed.z),
    });
    this.plateFilled.set(true);
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

  protected cancel(): void {
    this.cancelled.emit();
  }

  protected save(): void {
    this.form.markAllAsTouched();
    this.error.set(null);
    const invalid = this.validationError();
    if (invalid) {
      this.error.set(invalid);
      return;
    }
    const value = this.form.getRawValue();
    const input: PrinterInput = {
      model: value.model.trim(),
      buildPlate: { x: Number(value.x), y: Number(value.y), z: Number(value.z) },
      ownedNozzles: this.ownedNozzles(),
      defaultNozzle: this.defaultNozzle(),
      process: blankToNull(value.process),
      filament: blankToNull(value.filament),
    };
    const printer = this.printer();
    let request = this.settingsService.addPrinter(input);
    if (printer) {
      request = this.settingsService.updatePrinter(printer.id, input);
    }
    this.saving.set(true);
    request.subscribe({
      next: (settings) => {
        this.saving.set(false);
        this.saved.emit(settings);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(errorMessage(err, 'Could not save the printer.'));
      },
    });
  }

  /** The same rules as the backend, so the admin sees them before saving. */
  private validationError(): string | null {
    const controls = this.form.controls;
    if (controls.model.invalid || controls.model.value.trim() === '') {
      return 'Enter the printer model';
    }
    if (controls.x.invalid || controls.y.invalid || controls.z.invalid) {
      return `The build plate needs whole millimetres from ${MIN_PLATE_SIZE} to ${MAX_PLATE_SIZE}`;
    }
    const value = this.form.getRawValue();
    const complete =
      this.ownedNozzles().length > 0 &&
      this.defaultNozzle() !== null &&
      value.process.trim() !== '' &&
      value.filament.trim() !== '';
    if (this.slicingEnabled() && !complete) {
      return 'Choose nozzles and profiles for this printer while server slicing is on';
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

  private loadProfiles(): void {
    const nozzle = this.defaultNozzle();
    if (!this.selectedPrinter() || nozzle === null) {
      return;
    }
    this.settingsService.profiles(this.model(), nozzle).subscribe({
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
