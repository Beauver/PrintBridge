import { Component, computed, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FilamentInfo, ModelInfo, PlateInfo } from '../../../../core/models/model-info.model';
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
 */
@Component({
  selector: 'app-model-info-card',
  imports: [RouterLink],
  templateUrl: './model-info-card.html',
  styleUrl: './model-info-card.css',
})
export class ModelInfoCard {
  readonly info = input.required<ModelInfo>();
  /** The admin sees slicing errors, the plate size hint and the Re-slice button. */
  readonly isAdmin = input(false);
  /** True while a re-slice request is on its way. */
  readonly reslicing = input(false);
  readonly reslice = output<void>();

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

  protected readonly doesNotFit = computed(() => {
    return this.info().fits === false;
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

  /** "Sliced for a 0,4 mm nozzle", or which nozzle the project was made for. */
  protected readonly slicedFor = computed(() => {
    const slicing = this.info().slicing;
    if (slicing.status !== 'DONE' || slicing.nozzle === null) {
      return null;
    }
    if (slicing.madeForNozzle !== null) {
      return `Made for a ${formatNozzle(slicing.madeForNozzle)} nozzle · numbers for your ${formatNozzle(slicing.nozzle)}`;
    }
    return `Sliced for a ${formatNozzle(slicing.nozzle)} nozzle`;
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
