import { DOCUMENT } from '@angular/common';
import { inject } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';

/** A page that may have uploads running. */
export interface HasPendingUploads {
  hasPendingUploads(): boolean;
}

export const LEAVE_WHILE_UPLOADING =
  'Files are still uploading. Leave and cancel the remaining uploads?';

/**
 * Whether the page may be left: asks while files are uploading. Leaving destroys the page, which
 * cancels the uploads that haven't finished; files that are already up stay on the request.
 * Must run in an injection context.
 */
export function mayLeaveWhileUploading(page: HasPendingUploads): boolean {
  if (!page.hasPendingUploads()) {
    return true;
  }
  const view = inject(DOCUMENT).defaultView;
  if (!view) {
    return true;
  }
  return view.confirm(LEAVE_WHILE_UPLOADING);
}

/** Route guard for the pages that upload files. */
export const pendingUploadsGuard: CanDeactivateFn<HasPendingUploads> = mayLeaveWhileUploading;
