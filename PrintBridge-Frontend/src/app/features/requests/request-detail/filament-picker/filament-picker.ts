import { Component, computed, inject, input, output, signal, viewChild } from '@angular/core';
import {
  NgbDropdown,
  NgbDropdownAnchor,
  NgbDropdownMenu,
  PlacementArray,
} from '@ng-bootstrap/ng-bootstrap';
import {
  FilamentChoice,
  FilamentChoiceInput,
  FilamentSlot,
  SpoolmanFilament,
} from '../../../../core/models/spoolman.model';
import {
  choiceLabel,
  filamentBackground,
  filamentName,
  gramsLeftLabel,
  notEnoughFor,
  originalLabel,
} from '../../../../shared/format/filament-display';
import { SpoolmanFilamentsService } from '../../spoolman-filaments.service';

/** A row of the dropdown. */
interface FilamentOption {
  filament: SpoolmanFilament;
  name: string;
  background: string | null;
  left: string | null;
  notEnough: boolean;
}

/**
 * One colour circle of a file (#11): the file's colour, or the Spoolman filament chosen for it.
 * While choosing is allowed the circle is a button that opens a dropdown of every Spoolman
 * filament: same material first, with a search box and what is left of each. A filament with too
 * little left can still be chosen; it is marked, and the request then shows the warning.
 *
 * The menu is an ng-bootstrap dropdown attached to the body, so a scrolling plate table can't clip
 * it and it flips to whichever side of the circle has room.
 */
@Component({
  selector: 'app-filament-picker',
  imports: [NgbDropdown, NgbDropdownAnchor, NgbDropdownMenu],
  templateUrl: './filament-picker.html',
  styleUrl: './filament-picker.css',
})
export class FilamentPicker {
  private readonly filamentsService = inject(SpoolmanFilamentsService);
  private readonly dropdown = viewChild.required(NgbDropdown);

  /** The colour of a plate, or the whole item (all null). */
  readonly slot = input.required<FilamentSlot>();
  readonly choice = input<FilamentChoice | null>(null);
  /** The caller may choose a filament here: colour selection is on and the request may still change. */
  readonly editable = input(false);
  /** Grams this colour needs over all copies; null when unknown. Marks rows with too little left. */
  readonly gramsNeeded = input<number | null>(null);
  /** Automatic matching found no close filament for this colour (#76). */
  readonly unmatched = input(false);
  /** Also show the label next to the circle (single plate files, links). */
  readonly showLabel = input(false);
  readonly chosen = output<FilamentChoiceInput>();
  /** The id of the choice to remove: back to the file's colour. */
  readonly reset = output<number>();

  protected readonly open = signal(false);
  protected readonly search = signal('');
  protected readonly loading = this.filamentsService.loading;
  protected readonly error = this.filamentsService.error;
  /** Below the circle when there is room, else above, aligned to whichever side fits. */
  protected readonly placement: PlacementArray = [
    'bottom-start',
    'bottom-end',
    'top-start',
    'top-end',
  ];

  protected readonly background = computed(() => {
    const choice = this.choice();
    if (choice) {
      return filamentBackground(choice.color, choice.colors);
    }
    return this.slot().originalColor;
  });

  protected readonly label = computed(() => {
    const choice = this.choice();
    if (choice) {
      return choiceLabel(choice);
    }
    const slot = this.slot();
    if (slot.wholeItem) {
      if (this.editable()) {
        return 'Pick filament';
      }
      return 'No filament chosen';
    }
    return originalLabel(slot);
  });

  /** The tooltip: what is shown, how it was chosen, and that it can be clicked. */
  protected readonly title = computed(() => {
    let title = this.label();
    if (this.automatic()) {
      title = `${title} · Matched automatically`;
    }
    if (this.unmatched()) {
      title = `${title} · No close colour found`;
    }
    if (this.editable()) {
      return `${title} – click to choose a filament from Spoolman`;
    }
    return title;
  });

  /** Chosen by automatic matching (#76) rather than a person. */
  protected readonly automatic = computed(() => {
    return this.choice()?.source === 'AUTO';
  });

  protected readonly notEnough = computed(() => {
    return this.choice()?.notEnough ?? false;
  });

  /** Every filament matching the search; those of the colour's material first. */
  protected readonly options = computed<FilamentOption[]>(() => {
    const filaments = this.filamentsService.filaments() ?? [];
    const query = this.search().trim().toLowerCase();
    const material = this.slot().originalType?.toLowerCase() ?? null;
    const matching = filaments.filter((filament) => {
      if (!query) {
        return true;
      }
      const text = [filament.vendor, filament.name, filament.material].join(' ').toLowerCase();
      return text.includes(query);
    });
    const sameMaterial = matching.filter((filament) => {
      return material !== null && filament.material?.toLowerCase() === material;
    });
    const others = matching.filter((filament) => {
      return !sameMaterial.includes(filament);
    });
    return [...sameMaterial, ...others].map((filament) => {
      return {
        filament,
        name: filamentName(filament),
        background: filamentBackground(filament.color, filament.colors),
        left: gramsLeftLabel(filament),
        notEnough: notEnoughFor(filament, this.gramsNeeded()),
      };
    });
  });

  /** Loaded and nothing in Spoolman at all (not just nothing matching the search). */
  protected readonly empty = computed(() => {
    return this.filamentsService.filaments()?.length === 0;
  });

  protected toggle(): void {
    if (this.editable()) {
      this.dropdown().toggle();
    }
  }

  /** The dropdown opened or closed (click, Escape, a click outside); the menu is only built while open. */
  protected onOpenChange(open: boolean): void {
    this.open.set(open);
    if (open) {
      this.search.set('');
      this.filamentsService.load();
    }
  }

  protected close(): void {
    this.dropdown().close();
  }

  protected retry(): void {
    this.filamentsService.load(true);
  }

  protected onSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }

  protected pick(filament: SpoolmanFilament): void {
    const slot = this.slot();
    this.chosen.emit({
      wholeItem: slot.wholeItem,
      plateIndex: slot.plateIndex,
      originalType: slot.originalType,
      originalColor: slot.originalColor,
      spoolmanFilamentId: filament.id,
    });
    this.close();
  }

  protected useFileColour(): void {
    const choice = this.choice();
    if (choice) {
      this.reset.emit(choice.id);
    }
    this.close();
  }
}
