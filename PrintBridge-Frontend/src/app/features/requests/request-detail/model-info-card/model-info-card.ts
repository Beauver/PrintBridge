import {
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  OnInit,
  output,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { FilamentInfo, ModelInfo, PlateInfo } from '../../../../core/models/model-info.model';
import { PreviewColor } from '../../../../core/models/model-preview.model';
import {
  ColorSlot,
  FilamentChoice,
  FilamentChoiceInput,
  FilamentSlot,
} from '../../../../core/models/spoolman.model';
import { isChoiceFor, originalLabel } from '../../../../shared/format/filament-display';
import { FilamentPicker } from '../filament-picker/filament-picker';
import { ModelPreview } from '../../../../shared/model-viewer/model-preview';
import { ModelPreviewService } from '../../model-preview.service';
import {
  formatDimensions,
  formatDuration,
  formatGrams,
  formatNozzle,
  formatVolume,
} from '../../../../shared/format/model-info-display';

/**
 * What the backend knows about the model file: size, plates, weight, time and filaments, from
 * server slicing, the file's own slice data or an estimate (see docs/features/server-slicing.md).
 * Collapsible (#23): the header keeps the file name, quantity, weight, time and badges, plus what
 * the page projects with `cardStatus` (the item's status, #61); the body starts with whatever the
 * page projects into it (the file's actions).
 */
@Component({
  selector: 'app-model-info-card',
  imports: [RouterLink, ModelPreview, FilamentPicker],
  templateUrl: './model-info-card.html',
  styleUrl: './model-info-card.css',
})
export class ModelInfoCard implements OnInit {
  private readonly previewService = inject(ModelPreviewService);

  readonly info = input.required<ModelInfo>();
  /** The request and file this is about, for the 3D preview and plate images. */
  readonly requestId = input.required<number>();
  readonly fileId = input.required<number>();
  /** The admin sees slicing errors, the plate size hint and the Re-slice with… button. */
  readonly isAdmin = input(false);
  /** "Re-slice with…" (#21): the page opens the dialog. */
  readonly resliceWith = output<void>();
  /** Shown in the header; "Model" without one. */
  readonly filename = input<string | null>(null);
  /** Copies of this file, shown as "× 3". */
  readonly quantity = input(1);
  /** Whether the card starts open; only the first file's does on the request page. */
  readonly startOpen = input(true);
  /** The Spoolman filaments chosen for this file's colours (#11). */
  readonly filamentChoices = input<FilamentChoice[]>([]);
  /** The caller may choose filaments from Spoolman for this file's colours. */
  readonly filamentSelection = input(false);
  /** Shown when a chosen filament runs short (set by the admin). */
  readonly notEnoughMessage = input('');
  /** Colours automatic matching found no close filament for (#76). */
  readonly unmatchedColors = input<ColorSlot[]>([]);
  /** Shown while some colour has no close filament (set by the admin). */
  readonly noMatchMessage = input('');
  readonly filamentChosen = output<FilamentChoiceInput>();
  /** The id of a choice to remove. */
  readonly filamentReset = output<number>();

  protected readonly open = signal(false);
  private thumbnailsLoaded = false;

  /** Object URLs of the plate images Bambu files carry, by plate number. */
  protected readonly thumbnails = signal<ReadonlyMap<number, string>>(new Map());

  protected readonly previewLoader = computed(() => {
    const requestId = this.requestId();
    const fileId = this.fileId();
    return () => {
      return this.previewService.fileMesh(requestId, fileId);
    };
  });

  /** The image of the first plate with objects, shown until the 3D preview is loaded. */
  protected readonly placeholder = computed(() => {
    const thumbnails = this.thumbnails();
    const plate = this.info().plates.find((candidate) => {
      return candidate.width !== null && thumbnails.has(candidate.index);
    });
    if (!plate) {
      return null;
    }
    return thumbnails.get(plate.index) ?? null;
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      for (const url of this.thumbnails().values()) {
        URL.revokeObjectURL(url);
      }
    });
  }

  ngOnInit(): void {
    this.open.set(this.startOpen());
    if (this.open()) {
      this.loadThumbnails();
    }
  }

  protected toggle(): void {
    this.open.set(!this.open());
    if (this.open()) {
      this.loadThumbnails();
    }
  }

  /**
   * Once, when the card is first opened. Only Bambu projects have plate images; a plate without
   * one just shows none.
   */
  private loadThumbnails(): void {
    const info = this.info();
    if (this.thumbnailsLoaded || info.status !== 'ANALYZED' || info.source !== 'BAMBU') {
      return;
    }
    this.thumbnailsLoaded = true;
    for (const plate of info.plates) {
      this.previewService.thumbnail(this.requestId(), this.fileId(), plate.index).subscribe({
        next: (image) => {
          const thumbnails = new Map(this.thumbnails());
          thumbnails.set(plate.index, URL.createObjectURL(image));
          this.thumbnails.set(thumbnails);
        },
        error: () => {
          // No image for this plate.
        },
      });
    }
  }

  protected thumbnail(plate: PlateInfo): string | null {
    return this.thumbnails().get(plate.index) ?? null;
  }

  protected readonly analyzed = computed(() => {
    return this.info().status === 'ANALYZED';
  });

  /** Projects get a table per plate; an STL or plain 3MF is one block. */
  protected readonly showPlates = computed(() => {
    return this.info().source === 'BAMBU';
  });

  /** The only plate, for files without a plate table. */
  protected readonly singlePlate = computed(() => {
    return this.info().plates[0] ?? null;
  });

  protected readonly dimensions = computed(() => {
    const plate = this.singlePlate();
    if (!plate) {
      return null;
    }
    return this.plateDimensions(plate);
  });

  protected readonly volume = computed(() => {
    const volume = this.info().volumeCm3;
    if (volume === null) {
      return null;
    }
    return formatVolume(volume);
  });

  /** "65,25 g", or "~78 g" with a note when (partly) estimated. */
  protected readonly weight = computed(() => {
    const info = this.info();
    if (info.totalGrams === null) {
      return null;
    }
    if (info.weight === 'EXACT') {
      return { text: formatGrams(info.totalGrams), note: null };
    }
    let note = '(estimate)';
    if (info.weight === 'PARTLY_ESTIMATED') {
      note = '(partly estimated)';
    }
    return { text: `~${formatGrams(Math.round(info.totalGrams))}`, note };
  });

  /** "~78 g · 2 h 5 min" for the header; empty while nothing is known. */
  protected readonly headerSummary = computed(() => {
    const parts: string[] = [];
    const weight = this.weight();
    if (weight) {
      parts.push(weight.text);
    }
    const time = this.printTime();
    if (time) {
      parts.push(time);
    }
    return parts.join(' · ');
  });

  protected readonly printTime = computed(() => {
    const seconds = this.info().printSeconds;
    if (seconds === null) {
      return null;
    }
    return formatDuration(seconds);
  });

  protected readonly plateCount = computed(() => {
    const count = this.info().plates.length;
    if (count === 1) {
      return '1 plate';
    }
    return `${count} plates`;
  });

  /** "Too big for Bambu Lab P1S", when a plate doesn't fit. */
  protected readonly noFitText = computed(() => {
    const info = this.info();
    if (info.fits !== false) {
      return null;
    }
    if (info.fitPrinter) {
      return `Too big for ${info.fitPrinter}`;
    }
    return "Doesn't fit the build plate";
  });

  protected readonly showPlateSizeHint = computed(() => {
    return this.isAdmin() && this.analyzed() && this.info().fits === null;
  });

  protected readonly filaments = computed(() => {
    return this.singlePlate()?.filaments ?? [];
  });

  /** Queued or running on the slicer. */
  protected readonly slicingPending = computed(() => {
    const status = this.info().slicing.status;
    return status === 'QUEUED' || status === 'RUNNING';
  });

  /**
   * What the numbers are for: "Bambu Lab P1S · 0,4 mm nozzle". After a custom re-slice (#21)
   * also the chosen profiles: "… · 0.12mm Fine @BBL P1S · Bambu PLA Basic".
   */
  protected readonly slicedFor = computed(() => {
    const slicing = this.info().slicing;
    if (slicing.status !== 'DONE' || slicing.nozzle === null) {
      return null;
    }
    let target = `${formatNozzle(slicing.nozzle)} nozzle`;
    if (slicing.printer) {
      target = `${slicing.printer} · ${target}`;
    }
    if (!slicing.custom) {
      return target;
    }
    let process = "project's settings";
    if (slicing.process) {
      process = slicing.process;
    }
    let filament = "project's filaments";
    if (slicing.filament) {
      filament = slicing.filament;
    }
    return `${target} · ${process} · ${filament}`;
  });

  /**
   * The printer and nozzle a project was made for, only when it was sliced on another printer or
   * nozzle: "Bambu Lab A1 · 0,2 mm nozzle". The part that is the same comes from the slice.
   */
  protected readonly madeFor = computed(() => {
    const slicing = this.info().slicing;
    if (slicing.status !== 'DONE' || slicing.nozzle === null) {
      return null;
    }
    if (!slicing.madeForPrinter && slicing.madeForNozzle === null) {
      return null;
    }
    let nozzle = slicing.nozzle;
    if (slicing.madeForNozzle !== null) {
      nozzle = slicing.madeForNozzle;
    }
    let printer = slicing.printer;
    if (slicing.madeForPrinter) {
      printer = slicing.madeForPrinter;
    }
    if (!printer) {
      return `${formatNozzle(nozzle)} nozzle`;
    }
    return `${printer} · ${formatNozzle(nozzle)} nozzle`;
  });

  /** Shown in the header, so a collapsed card still flags it; the reason stays in the body (admin). */
  protected readonly slicingFailed = computed(() => {
    return this.info().slicing.status === 'FAILED';
  });

  /** Only the admin gets the reason; for others the card just falls back. */
  protected readonly slicingError = computed(() => {
    const slicing = this.info().slicing;
    if (slicing.status !== 'FAILED') {
      return null;
    }
    return slicing.error;
  });

  protected readonly canReslice = computed(() => {
    return this.isAdmin() && this.info().slicing.enabled && !this.slicingPending();
  });

  protected plateLabel(plate: PlateInfo): string {
    if (plate.name) {
      return `${plate.index}. ${plate.name}`;
    }
    return `Plate ${plate.index}`;
  }

  protected plateDimensions(plate: PlateInfo): string | null {
    if (plate.width === null || plate.depth === null || plate.height === null) {
      return null;
    }
    return formatDimensions(plate.width, plate.depth, plate.height);
  }

  /** "33,69 g", or "~4 g" for an estimate. */
  protected plateGrams(plate: PlateInfo): string | null {
    if (plate.grams === null) {
      return null;
    }
    if (plate.estimated) {
      return `~${formatGrams(Math.round(plate.grams))}`;
    }
    return formatGrams(plate.grams);
  }

  protected plateTime(plate: PlateInfo): string | null {
    if (plate.printSeconds === null) {
      return null;
    }
    return formatDuration(plate.printSeconds);
  }

  protected filamentLabel(filament: FilamentInfo): string {
    const parts: string[] = [];
    if (filament.type) {
      parts.push(filament.type);
    }
    if (filament.color) {
      parts.push(filament.color);
    }
    if (filament.grams !== null) {
      parts.push(formatGrams(filament.grams));
    }
    if (parts.length === 0) {
      return 'Unknown filament';
    }
    return parts.join(' · ');
  }

  /** The file has colours to choose filaments for; otherwise the whole file gets one. */
  protected readonly hasColours = computed(() => {
    return (
      this.analyzed() &&
      this.info().plates.some((plate) => {
        return plate.filaments.length > 0;
      })
    );
  });

  protected readonly wholeItemSlot: FilamentSlot = {
    wholeItem: true,
    plateIndex: null,
    originalType: null,
    originalColor: null,
  };

  protected readonly wholeItemChoice = computed(() => {
    return this.choiceFor(this.wholeItemSlot);
  });

  /** The single "pick a filament" circle, for a file without colours. */
  protected readonly showWholeItem = computed(() => {
    return !this.hasColours() && (this.filamentSelection() || this.wholeItemChoice() !== null);
  });

  /** The file's total over all copies; null when unknown. */
  protected readonly wholeItemGrams = computed(() => {
    const total = this.info().totalGrams;
    if (total === null || total === undefined) {
      return null;
    }
    return total * this.quantity();
  });

  /** Choices for colours this file no longer has (e.g. after a re-slice). */
  protected readonly orphanedChoices = computed(() => {
    return this.filamentChoices().filter((choice) => {
      return choice.orphaned;
    });
  });

  protected readonly notEnoughFilament = computed(() => {
    const short = this.filamentChoices().some((choice) => {
      return choice.notEnough && !choice.orphaned;
    });
    if (short) {
      return this.notEnoughMessage();
    }
    return null;
  });

  /**
   * The chosen filaments as colours for the 3D preview: each replaces its colour on its plate, a
   * whole-file choice colours a file without colours. Multi-colour filaments show their first.
   */
  protected readonly previewColors = computed<PreviewColor[]>(() => {
    const colors: PreviewColor[] = [];
    for (const choice of this.filamentChoices()) {
      const to = choice.color ?? choice.colors[0] ?? null;
      if (choice.orphaned || to === null) {
        continue;
      }
      colors.push({
        wholeFile: choice.wholeItem,
        plate: choice.plateIndex,
        from: choice.originalColor,
        to,
      });
    }
    return colors;
  });

  /** The plate the 3D preview shows; null until it is opened. */
  protected readonly previewPlate = signal<number | null>(null);

  /**
   * The plate whose colours are shown under the preview's plate buttons, so they can be changed
   * while watching the model (#11): only while choosing is allowed or something was chosen.
   */
  protected readonly previewPlateColours = computed<PlateInfo | null>(() => {
    const index = this.previewPlate();
    if (!this.hasColours() || index === null) {
      return null;
    }
    if (!this.filamentSelection() && this.filamentChoices().length === 0) {
      return null;
    }
    const plate = this.info().plates.find((candidate) => {
      return candidate.index === index;
    });
    if (!plate || plate.filaments.length === 0) {
      return null;
    }
    return plate;
  });

  protected onPreviewPlate(plate: number): void {
    this.previewPlate.set(plate);
  }

  protected slotOf(plate: PlateInfo, filament: FilamentInfo): FilamentSlot {
    return {
      wholeItem: false,
      plateIndex: plate.index,
      originalType: filament.type,
      originalColor: filament.color,
    };
  }

  /** The no-match warning (#76) for a plate with a colour no close filament was found for. */
  protected noMatchOn(plate: PlateInfo): string | null {
    const unmatched = plate.filaments.some((filament) => {
      return this.isUnmatched(this.slotOf(plate, filament));
    });
    if (!unmatched) {
      return null;
    }
    return this.noMatchMessage();
  }

  /** Whether automatic matching found no close filament for this colour (#76). */
  protected isUnmatched(slot: FilamentSlot): boolean {
    return this.unmatchedColors().some((unmatched) => {
      return isChoiceFor({ ...unmatched, wholeItem: false }, slot);
    });
  }

  /** Whether this colour's filament was matched automatically (#76). */
  protected isAutomatic(slot: FilamentSlot): boolean {
    return this.choiceFor(slot)?.source === 'AUTO';
  }

  protected choiceFor(slot: FilamentSlot): FilamentChoice | null {
    const choice = this.filamentChoices().find((candidate) => {
      return !candidate.orphaned && isChoiceFor(candidate, slot);
    });
    return choice ?? null;
  }

  /**
   * Grams of this colour over all copies, like the backend counts them: the colour's own sliced
   * grams, else the plate's when it is the plate's only colour; null when unknown.
   */
  protected gramsNeeded(plate: PlateInfo, filament: FilamentInfo): number | null {
    let grams = filament.grams;
    if (grams === null && plate.filaments.length === 1) {
      grams = plate.grams;
    }
    if (grams === null) {
      return null;
    }
    return grams * this.quantity();
  }

  /**
   * Whether a filament was chosen for any colour of this plate. Then the colour rows show only
   * the circles (names in their tooltips) and an "Old colours" row with the file's own colours.
   */
  protected hasChoiceOn(plate: PlateInfo): boolean {
    return plate.filaments.some((filament) => {
      return this.choiceFor(this.slotOf(plate, filament)) !== null;
    });
  }

  /** "Plate 2 · PLA #FFFFFF" for a choice the file no longer has. */
  protected orphanLabel(choice: FilamentChoice): string {
    if (choice.wholeItem) {
      return 'Whole file';
    }
    return `Plate ${choice.plateIndex} · ${originalLabel(choice)}`;
  }

  protected chooseFilament(choice: FilamentChoiceInput): void {
    this.filamentChosen.emit(choice);
  }

  protected resetFilament(choiceId: number): void {
    this.filamentReset.emit(choiceId);
  }

  protected requestResliceWith(): void {
    this.resliceWith.emit();
  }
}
