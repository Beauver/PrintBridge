import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { errorMessage } from '../../../../core/http/api-error';
import {
  ReslicePrinter,
  ResliceOptions,
  SlicerProfiles,
} from '../../../../core/models/slicer.model';
import { formatNozzle } from '../../../../shared/format/model-info-display';
import { SettingsService } from '../../../settings/settings.service';
import { PrintRequestsService } from '../../print-requests.service';

/** The process select's value for "Project's own settings". */
export const PROJECT_OWN = '';

/** What the filament field holds for the project's filaments; offered as a suggestion. */
export const PROJECT_FILAMENTS = "Project's filaments";

/**
 * "Re-slice with…" (#21): the admin picks a printer, nozzle, process and filament profile for one
 * file. Opened with NgbModal by the request page, which sets the file before the first check;
 * closes with the updated request, or is dismissed.
 */
@Component({
  selector: 'app-reslice-dialog',
  templateUrl: './reslice-dialog.html',
})
export class ResliceDialog implements OnInit {
  private readonly modal = inject(NgbActiveModal);
  private readonly requestsService = inject(PrintRequestsService);
  private readonly settingsService = inject(SettingsService);

  /** Set by the page right after opening. */
  requestId = 0;
  fileId = 0;
  filename = '';

  protected readonly options = signal<ResliceOptions | null>(null);
  protected readonly loading = signal(true);
  /** The options couldn't be loaded; shown instead of the form. */
  protected readonly loadError = signal<string | null>(null);
  protected readonly printerId = signal<number | null>(null);
  protected readonly nozzle = signal<number | null>(null);
  /** `PROJECT_OWN` for the project's own settings. */
  protected readonly process = signal<string>(PROJECT_OWN);
  /**
   * What is typed in the filament field: a listed profile, `PROJECT_FILAMENTS`, or anything
   * while the admin is still searching (then it can't be sliced).
   */
  protected readonly filament = signal<string>('');
  protected readonly profiles = signal<SlicerProfiles | null>(null);
  protected readonly profilesLoading = signal(false);
  /** The profiles couldn't be loaded (e.g. the slicer can't be reached). */
  protected readonly profilesError = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);

  protected readonly projectOwn = PROJECT_OWN;
  protected readonly projectFilaments = PROJECT_FILAMENTS;
  protected readonly formatNozzle = formatNozzle;

  protected readonly printer = computed(() => {
    const options = this.options();
    if (!options) {
      return null;
    }
    return (
      options.printers.find((printer) => {
        return printer.id === this.printerId();
      }) ?? null
    );
  });

  /** The project's own process only on the printer and nozzle it was made for (backend checks too). */
  protected readonly ownSettingsPossible = computed(() => {
    const options = this.options();
    const printer = this.printer();
    if (!options?.project || !printer || !options.projectPrinter) {
      return false;
    }
    return (
      printer.model.toLowerCase() === options.projectPrinter.trim().toLowerCase() &&
      options.projectNozzle === this.nozzle()
    );
  });

  protected readonly projectFilamentsPossible = computed(() => {
    return this.options()?.project ?? false;
  });

  /** Only a listed profile, or the project's filaments for a project, can be sliced with. */
  protected readonly filamentValid = computed(() => {
    const filament = this.filament();
    if (filament === PROJECT_FILAMENTS) {
      return this.projectFilamentsPossible();
    }
    return this.profiles()?.filaments.includes(filament) ?? false;
  });

  /** Shown under the field once something is typed that isn't on the list. */
  protected readonly filamentError = computed(() => {
    if (!this.profiles() || this.filament().trim() === '' || this.filamentValid()) {
      return null;
    }
    return 'Choose a filament profile from the list';
  });

  protected readonly canSlice = computed(() => {
    if (!this.printer() || this.nozzle() === null || this.profilesLoading() || this.saving()) {
      return false;
    }
    if (!this.profiles()) {
      return false;
    }
    if (this.process() === PROJECT_OWN && !this.ownSettingsPossible()) {
      return false;
    }
    return this.filamentValid();
  });

  ngOnInit(): void {
    this.requestsService.resliceOptions(this.requestId, this.fileId).subscribe({
      next: (options) => {
        this.options.set(options);
        this.loading.set(false);
        if (options.printers.length === 0) {
          this.loadError.set('Add a printer with nozzles and profiles in Settings first.');
          return;
        }
        const start = options.start;
        this.printerId.set(start.printerId ?? options.printers[0].id);
        this.nozzle.set(start.nozzle ?? this.printer()?.defaultNozzle ?? null);
        this.process.set(start.process ?? PROJECT_OWN);
        this.filament.set(start.filament ?? PROJECT_FILAMENTS);
        this.loadProfiles();
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.loadError.set(errorMessage(err, 'Could not load the printers.'));
      },
    });
  }

  /** Another printer: its default nozzle (or the same size if it has one), and its profiles. */
  protected onPrinterChange(event: Event): void {
    this.printerId.set(Number((event.target as HTMLSelectElement).value));
    const printer = this.printer();
    if (printer && !printer.ownedNozzles.includes(this.nozzle() ?? -1)) {
      this.nozzle.set(printer.defaultNozzle);
    }
    this.loadProfiles();
  }

  protected onNozzleChange(event: Event): void {
    this.nozzle.set(Number((event.target as HTMLSelectElement).value));
    this.loadProfiles();
  }

  protected onProcessChange(event: Event): void {
    this.process.set((event.target as HTMLSelectElement).value);
  }

  /** Typing filters the suggestions; picking one fills the field. */
  protected onFilamentInput(event: Event): void {
    this.filament.set((event.target as HTMLInputElement).value);
  }

  protected slice(): void {
    if (!this.canSlice()) {
      return;
    }
    this.saving.set(true);
    this.saveError.set(null);
    this.requestsService
      .resliceWith(this.requestId, this.fileId, {
        printerId: this.printerId(),
        nozzle: this.nozzle(),
        process: nullForProjectOwn(this.process()),
        filament: this.chosenFilament(),
      })
      .subscribe({
        next: (request) => {
          this.saving.set(false);
          this.modal.close(request);
        },
        error: (err: unknown) => {
          this.saving.set(false);
          this.saveError.set(errorMessage(err, 'Could not start slicing.'));
        },
      });
  }

  protected cancel(): void {
    this.modal.dismiss();
  }

  /** `null` for the project's filaments, as the API expects. */
  private chosenFilament(): string | null {
    if (this.filament() === PROJECT_FILAMENTS) {
      return null;
    }
    return this.filament();
  }

  /** The process and filament profiles for the chosen printer and nozzle. */
  private loadProfiles(): void {
    const printer = this.printer();
    const nozzle = this.nozzle();
    if (!printer || nozzle === null) {
      return;
    }
    this.profiles.set(null);
    this.profilesError.set(null);
    this.profilesLoading.set(true);
    this.settingsService.profiles(printer.model, nozzle).subscribe({
      next: (profiles) => {
        this.profilesLoading.set(false);
        this.profiles.set(profiles);
        this.pickProfiles(printer, profiles);
      },
      error: (err: unknown) => {
        this.profilesLoading.set(false);
        this.profilesError.set(errorMessage(err, 'Could not load the profiles from the slicer.'));
      },
    });
  }

  /**
   * Keeps the chosen profiles where they still fit, else falls back: the project's own where
   * possible, else the printer's own profile, else the first one listed.
   */
  private pickProfiles(printer: ReslicePrinter, profiles: SlicerProfiles): void {
    this.process.set(
      pick(
        this.process(),
        profiles.processes,
        this.ownSettingsPossible(),
        printer.process,
        PROJECT_OWN,
      ),
    );
    this.filament.set(
      pick(
        this.filament(),
        profiles.filaments,
        this.projectFilamentsPossible(),
        printer.filament,
        PROJECT_FILAMENTS,
      ),
    );
  }
}

/**
 * @param projectOwn the value that stands for the project's own settings / filaments; also
 *                   what is left when nothing fits (the admin then has to choose)
 */
function pick(
  current: string,
  available: string[],
  projectOwnPossible: boolean,
  printerDefault: string | null,
  projectOwn: string,
): string {
  if (current === projectOwn && projectOwnPossible) {
    return current;
  }
  if (current !== projectOwn && available.includes(current)) {
    return current;
  }
  if (projectOwnPossible) {
    return projectOwn;
  }
  if (printerDefault && available.includes(printerDefault)) {
    return printerDefault;
  }
  return available[0] ?? projectOwn;
}

function nullForProjectOwn(value: string): string | null {
  if (value === PROJECT_OWN) {
    return null;
  }
  return value;
}
