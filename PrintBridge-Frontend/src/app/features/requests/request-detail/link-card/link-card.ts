import { Component, computed, input, OnInit, output, signal } from '@angular/core';
import {
  FilamentChoice,
  FilamentChoiceInput,
  FilamentSlot,
} from '../../../../core/models/spoolman.model';
import { NOT_ENOUGH_FILAMENT } from '../../../../shared/format/filament-display';
import { linkLabel } from '../../../../shared/format/print-request-display';
import { FilamentPicker } from '../filament-picker/filament-picker';

/**
 * A link of a request (#59), in the same list as the Model cards and collapsible like them. A
 * link has no preview, model details or slicing; the body is whatever the page projects into it
 * (the link's actions, with Open link and the URL), the header also takes what is projected with
 * `cardStatus` (the item's status, #61). A link gets one filament for the whole model (#11).
 */
@Component({
  selector: 'app-link-card',
  imports: [FilamentPicker],
  templateUrl: './link-card.html',
  styleUrl: './link-card.css',
})
export class LinkCard implements OnInit {
  readonly url = input.required<string>();
  readonly linkName = input<string | null>(null);
  /** Copies, shown as "× 3". */
  readonly quantity = input(1);
  /** Whether the card starts open; only the first entry's does on the request page. */
  readonly startOpen = input(true);
  /** At most one: the filament chosen for the whole link (#11). */
  readonly filamentChoices = input<FilamentChoice[]>([]);
  /** The circle can be clicked to choose a filament from Spoolman. */
  readonly filamentSelection = input(false);
  readonly filamentChosen = output<FilamentChoiceInput>();
  /** The id of a choice to remove. */
  readonly filamentReset = output<number>();

  protected readonly wholeItemSlot: FilamentSlot = {
    plateIndex: null,
    originalType: null,
    originalColor: null,
  };

  protected readonly choice = computed(() => {
    return this.filamentChoices()[0] ?? null;
  });

  protected readonly showFilament = computed(() => {
    return this.filamentSelection() || this.choice() !== null;
  });

  protected readonly notEnoughFilament = computed(() => {
    if (this.choice()?.notEnough) {
      return NOT_ENOUGH_FILAMENT;
    }
    return null;
  });

  protected readonly open = signal(false);

  /** The name, or the URL's host. */
  protected readonly label = computed(() => {
    return linkLabel(this.url(), this.linkName());
  });

  ngOnInit(): void {
    this.open.set(this.startOpen());
  }

  protected toggle(): void {
    this.open.set(!this.open());
  }

  protected chooseFilament(choice: FilamentChoiceInput): void {
    this.filamentChosen.emit(choice);
  }

  protected resetFilament(choiceId: number): void {
    this.filamentReset.emit(choiceId);
  }
}
