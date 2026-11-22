import { FilamentChoice, FilamentSlot, SpoolmanFilament } from '../../core/models/spoolman.model';
import { formatGrams } from './model-info-display';

/** The parts of a filament the labels use; a Spoolman filament and a choice both have them. */
type FilamentNaming = Pick<SpoolmanFilament, 'name' | 'vendor' | 'material'>;

/** "Bambu Lab PLA Basic – PLA"; "Filament" when Spoolman knows nothing about it. */
export function filamentName(filament: FilamentNaming): string {
  const parts: string[] = [];
  if (filament.vendor) {
    parts.push(filament.vendor);
  }
  if (filament.name) {
    parts.push(filament.name);
  }
  let name = parts.join(' ');
  if (!name) {
    name = 'Filament';
  }
  if (filament.material) {
    return `${name} – ${filament.material}`;
  }
  return name;
}

/**
 * The CSS background of a filament's circle: its colour, or a multi-colour filament split into
 * equal parts; null when no colour is known.
 */
export function filamentBackground(color: string | null, colors: string[]): string | null {
  if (colors.length > 1) {
    const step = 100 / colors.length;
    const stops = colors.map((part, index) => {
      return `${part} ${index * step}% ${(index + 1) * step}%`;
    });
    return `linear-gradient(90deg, ${stops.join(', ')})`;
  }
  if (colors.length === 1) {
    return colors[0];
  }
  return color;
}

/** What the file says about a colour: "PLA #FFFFFF", "PLA", "#FFFFFF" or "file colour". */
export function originalLabel(slot: FilamentSlot): string {
  const parts: string[] = [];
  if (slot.originalType) {
    parts.push(slot.originalType);
  }
  if (slot.originalColor) {
    parts.push(slot.originalColor);
  }
  if (parts.length === 0) {
    return 'file colour';
  }
  return parts.join(' ');
}

/** "Acme PLA Basic – PLA (was PLA #FFFFFF)", plus a note when Spoolman no longer has it. */
export function choiceLabel(choice: FilamentChoice): string {
  let label = filamentName(choice);
  if (!choice.wholeItem) {
    label = `${label} (was ${originalLabel(choice)})`;
  }
  if (choice.inSpoolman === false) {
    label = `${label} · no longer in Spoolman`;
  }
  return label;
}

/** "412 g left", or null when Spoolman doesn't know. */
export function gramsLeftLabel(filament: SpoolmanFilament): string | null {
  if (filament.gramsLeft === null) {
    return null;
  }
  return `${formatGrams(Math.round(filament.gramsLeft))} left`;
}

/**
 * Whether a filament can't cover `gramsNeeded` (nothing left at all always counts). An unknown
 * amount left never warns.
 */
export function notEnoughFor(filament: SpoolmanFilament, gramsNeeded: number | null): boolean {
  if (filament.gramsLeft === null) {
    return false;
  }
  if (filament.gramsLeft <= 0) {
    return true;
  }
  return gramsNeeded !== null && gramsNeeded > filament.gramsLeft;
}

/** Whether the choice is for this colour of this plate (colours ignore case). */
export function isChoiceFor(choice: FilamentChoice, slot: FilamentSlot): boolean {
  return (
    choice.wholeItem === slot.wholeItem &&
    choice.plateIndex === slot.plateIndex &&
    choice.originalType === slot.originalType &&
    sameColor(choice.originalColor, slot.originalColor)
  );
}

function sameColor(firstColor: string | null, secondColor: string | null): boolean {
  if (firstColor === null || secondColor === null) {
    return firstColor === secondColor;
  }
  return firstColor.toLowerCase() === secondColor.toLowerCase();
}
