import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FilamentInfo, ModelInfo, PlateInfo } from '../../../../core/models/model-info.model';
import {
  formatDimensions,
  formatDuration,
  formatGrams,
  formatVolume,
} from '../../../../shared/model-info-display';

/** What the backend read from the model file: size, plates, weight, time and filaments. */
@Component({
  selector: 'app-model-info-card',
  imports: [RouterLink],
  templateUrl: './model-info-card.html',
  styleUrl: './model-info-card.css',
})
export class ModelInfoCard {
  readonly info = input.required<ModelInfo>();
  /** The admin gets a hint to set the build plate size when it is missing. */
  readonly isAdmin = input(false);

  protected readonly analyzed = computed(() => {
    return this.info().status === 'ANALYZED';
  });

  /** Project files get a table per plate; an STL or plain 3MF is one block. */
  protected readonly showPlates = computed(() => {
    return this.info().source === 'BAMBU' || this.info().source === 'PRUSA';
  });

  /** Size of the only plate, for files without a plate table. */
  protected readonly dimensions = computed(() => {
    const plate = this.info().plates[0];
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

  /** Exact grams when every plate is sliced, otherwise the estimate. */
  protected readonly weight = computed(() => {
    const info = this.info();
    if (info.slicedGrams !== null) {
      return { text: formatGrams(info.slicedGrams), estimate: false };
    }
    if (info.estimatedGrams !== null) {
      return { text: `~${formatGrams(info.estimatedGrams)}`, estimate: true };
    }
    return null;
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

  /** Filaments of the single-plate view (PrusaSlicer files have them; STLs don't). */
  protected readonly filaments = computed(() => {
    const plate = this.info().plates[0];
    if (!plate) {
      return [];
    }
    return plate.filaments;
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

  protected plateGrams(plate: PlateInfo): string | null {
    if (plate.grams === null) {
      return null;
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
}
