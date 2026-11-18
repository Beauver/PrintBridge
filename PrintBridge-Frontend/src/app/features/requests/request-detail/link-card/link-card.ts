import { Component, computed, input, OnInit, signal } from '@angular/core';
import { linkLabel } from '../../../../shared/format/print-request-display';

/**
 * A link of a request (#59), in the same list as the Model cards and collapsible like them. A
 * link has no preview, model details or slicing; the body is whatever the page projects into it
 * (the link's actions, with Open link and the URL).
 */
@Component({
  selector: 'app-link-card',
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
}
