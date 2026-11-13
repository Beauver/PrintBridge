import { Component, computed, inject, input, output, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { errorMessage } from '../../../core/http/api-error';
import { Printer, Settings } from '../../../core/models/settings.model';
import { SlicerPrinter } from '../../../core/models/slicer.model';
import { formatDimensions, formatNozzle } from '../../../shared/model-info-display';
import { SettingsService } from '../settings.service';
import { PrinterForm } from '../printer-form/printer-form';

/** Which printer is being edited: an id, a new one, or none. */
type Editing = number | 'new' | null;

/**
 * The printers the admin owns. Each has a build plate for the fit check and, for server
 * slicing, owned nozzles and profiles. The default printer is used for STL files and projects
 * made for a printer that isn't owned.
 */
@Component({
  selector: 'app-printers-card',
  imports: [PrinterForm],
  templateUrl: './printers-card.html',
})
export class PrintersCard {
  private readonly settingsService = inject(SettingsService);

  readonly settings = input.required<Settings>();
  /** What the slicer offers; empty when it can't be reached. */
  readonly slicerPrinters = input<SlicerPrinter[]>([]);
  readonly changed = output<Settings>();

  protected readonly editing = signal<Editing>(null);
  protected readonly confirmingDeleteId = signal<number | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly printers = computed(() => {
    return this.settings().printers;
  });

  protected readonly slicingEnabled = computed(() => {
    return this.settings().slicing.enabled;
  });

  protected readonly editingPrinter = computed(() => {
    const editing = this.editing();
    return (
      this.printers().find((printer) => {
        return printer.id === editing;
      }) ?? null
    );
  });

  protected isDefault(printer: Printer): boolean {
    return printer.id === this.settings().defaultPrinterId;
  }

  protected plateText(printer: Printer): string {
    const plate = printer.buildPlate;
    return formatDimensions(plate.x, plate.y, plate.z);
  }

  /** "0,2 mm · 0,4 mm (default)", or a hint when slicing isn't set up for it. */
  protected nozzleText(printer: Printer): string {
    if (printer.ownedNozzles.length === 0) {
      return 'No nozzles chosen';
    }
    return printer.ownedNozzles
      .map((nozzle) => {
        if (nozzle === printer.defaultNozzle) {
          return `${formatNozzle(nozzle)} (default)`;
        }
        return formatNozzle(nozzle);
      })
      .join(' · ');
  }

  protected startAdding(): void {
    this.clearMessages();
    this.editing.set('new');
  }

  protected startEditing(printer: Printer): void {
    this.clearMessages();
    this.editing.set(printer.id);
  }

  protected stopEditing(): void {
    this.editing.set(null);
  }

  protected onSaved(settings: Settings): void {
    this.editing.set(null);
    this.notice.set('Printer saved.');
    this.changed.emit(settings);
  }

  protected askDelete(printer: Printer): void {
    this.clearMessages();
    this.confirmingDeleteId.set(printer.id);
  }

  protected cancelDelete(): void {
    this.confirmingDeleteId.set(null);
  }

  protected deletePrinter(printer: Printer): void {
    this.run(this.settingsService.deletePrinter(printer.id), `${printer.model} removed.`, 'remove');
  }

  protected makeDefault(printer: Printer): void {
    this.clearMessages();
    this.run(
      this.settingsService.makeDefaultPrinter(printer.id),
      `${printer.model} is now the default printer.`,
      'change',
    );
  }

  private run(request: Observable<Settings>, notice: string, action: string): void {
    this.busy.set(true);
    request.subscribe({
      next: (settings) => {
        this.busy.set(false);
        this.confirmingDeleteId.set(null);
        this.notice.set(notice);
        this.changed.emit(settings);
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.confirmingDeleteId.set(null);
        this.error.set(errorMessage(err, `Could not ${action} the printer.`));
      },
    });
  }

  private clearMessages(): void {
    this.error.set(null);
    this.notice.set(null);
  }
}
