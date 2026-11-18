import { Component, input, output, signal } from '@angular/core';
import { NewLink } from '../../../core/models/print-request.model';
import { checkLink, MAX_LINK_NAME_LENGTH, MAX_URL_LENGTH } from '../upload-checks';

/** Gives every form its own field ids, since the page may show it next to other forms. */
let nextFormId = 1;

/**
 * The inline "Add link" row (#59): a URL and an optional name. Checks the link like the backend
 * does and hands it over; the server never fetches it. Not a `<form>`, because it sits inside
 * the new request form.
 */
@Component({
  selector: 'app-link-form',
  templateUrl: './link-form.html',
})
export class LinkForm {
  readonly disabled = input(false);
  readonly added = output<NewLink>();
  readonly cancelled = output<void>();

  protected readonly formId = nextFormId++;
  protected readonly url = signal('');
  protected readonly name = signal('');
  protected readonly error = signal<string | null>(null);

  protected readonly maxUrlLength = MAX_URL_LENGTH;
  protected readonly maxNameLength = MAX_LINK_NAME_LENGTH;

  protected setUrl(event: Event): void {
    this.url.set((event.target as HTMLInputElement).value);
    this.error.set(null);
  }

  protected setName(event: Event): void {
    this.name.set((event.target as HTMLInputElement).value);
    this.error.set(null);
  }

  /** Enter adds the link instead of submitting the surrounding form. */
  protected onEnter(event: Event): void {
    event.preventDefault();
    this.add();
  }

  protected add(): void {
    const link: NewLink = { url: this.url().trim(), name: this.name().trim() };
    const error = checkLink(link);
    if (error) {
      this.error.set(error);
      return;
    }
    this.added.emit(link);
    this.url.set('');
    this.name.set('');
  }

  protected cancel(): void {
    this.cancelled.emit();
  }
}
