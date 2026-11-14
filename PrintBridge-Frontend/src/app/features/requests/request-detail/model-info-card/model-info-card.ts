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
import { ModelPreview } from '../../../../shared/model-viewer/model-preview';
import { ModelPreviewService } from '../../model-preview.service';
import {
  formatDimensions,
  formatDuration,
  formatGrams,
  formatNozzle,
  formatVolume,
} from '../../../../shared/model-info-display';

/**
 * What the backend knows about the model file: size, plates, weight, time and filaments, from
 * server slicing, the file's own slice data or an estimate (see docs/features/server-slicing.md).
 * Collapsible (#23): the header keeps the file name, quantity, weight, time and badges; the body
 * starts with whatever the page projects into it (the file's actions).
 */
@Component({
  selector: 'app-model-info-card',
  imports: [RouterLink, ModelPreview],
  templateUrl: './model-info-card.html',
  styleUrl: './model-info-card.css',
})
export class ModelInfoCard implements OnInit {
  private readonly previewService = inject(ModelPreviewService);

  readonly info = input.required<ModelInfo>();
  /** The request and file this is about, for the 3D preview and plate images. */
  readonly requestId = input.required<number>();
  readonly fileId = input.required<number>();
  /** The admin sees slicing errors, the plate size hint and the Re-slice button. */
  readonly isAdmin = input(false);
  /** True while a re-slice request is on its way. */
  readonly reslicing = input(false);
  readonly reslice = output<void>();
  /** Shown in the header; "Model" without one. */
  readonly filename = input<string | null>(null);
  /** Copies of this file, shown as "× 3". */
  readonly quantity = input(1);
  /** Whether the card starts open; only the first file's does on the request page. */
  readonly startOpen = input(true);

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
   * "Bambu Lab P1S · 0,4 mm nozzle", or what the project was made for when it was sliced on
   * another printer or nozzle.
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
    let madeFor: string | null = null;
    if (slicing.madeForPrinter && slicing.madeForNozzle !== null) {
      madeFor = `${slicing.madeForPrinter} · ${formatNozzle(slicing.madeForNozzle)} nozzle`;
    } else if (slicing.madeForPrinter) {
      madeFor = slicing.madeForPrinter;
    } else if (slicing.madeForNozzle !== null) {
      madeFor = `a ${formatNozzle(slicing.madeForNozzle)} nozzle`;
    }
    if (madeFor) {
      return `Made for ${madeFor}. Numbers are for your ${target}`;
    }
    return target;
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

  protected requestReslice(): void {
    this.reslice.emit();
  }
}
