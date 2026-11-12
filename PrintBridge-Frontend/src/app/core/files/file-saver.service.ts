import { DOCUMENT } from '@angular/common';
import { inject, Injectable } from '@angular/core';

/**
 * Hands a downloaded blob to the browser as a file. Downloads go through HttpClient (so the
 * access token is sent) instead of a plain link, hence this step.
 */
@Injectable({ providedIn: 'root' })
export class FileSaverService {
  private readonly document = inject(DOCUMENT);

  save(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const link = this.document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    // Revoke on the next tick: the click starts the download synchronously in all browsers.
    setTimeout(() => {
      URL.revokeObjectURL(url);
    });
  }
}
