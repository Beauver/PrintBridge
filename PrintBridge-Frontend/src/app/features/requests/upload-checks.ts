import { HttpErrorResponse } from '@angular/common/http';
import { errorMessage } from '../../core/http/api-error';
import { UploadConfig } from '../../core/models/print-request.model';
import { formatBytes } from '../../shared/print-request-display';

/** Used until the upload config is loaded (or if loading it fails); the backend checks anyway. */
export const FALLBACK_CONFIG: UploadConfig = {
  maxSizeBytes: 0,
  maxFiles: 0,
  allowedExtensions: ['stl', '3mf'],
};

/** Same range as the backend. */
export const MIN_QUANTITY = 1;
export const MAX_QUANTITY = 100;

/** Lower-case extension without the dot, or '' when the name has none. */
export function extensionOf(filename: string): string {
  const dot = filename.lastIndexOf('.');
  if (dot < 0) {
    return '';
  }
  return filename.substring(dot + 1).toLowerCase();
}

/** ['stl', '3mf'] → ['.stl', '.3mf'] */
export function dotted(extensions: string[]): string[] {
  return extensions.map((extension) => {
    return `.${extension}`;
  });
}

/** Client-side file check, mirroring the backend (which also checks the content). */
export function checkFile(file: File, config: UploadConfig): string | null {
  if (!config.allowedExtensions.includes(extensionOf(file.name))) {
    return `Only ${dotted(config.allowedExtensions).join(', ')} files are allowed`;
  }
  if (file.size === 0) {
    return 'The file is empty';
  }
  if (config.maxSizeBytes > 0 && file.size > config.maxSizeBytes) {
    return `The file is larger than ${formatBytes(config.maxSizeBytes)}`;
  }
  return null;
}

/** A whole number from 1 to 100. */
export function isValidQuantity(quantity: number): boolean {
  return Number.isInteger(quantity) && quantity >= MIN_QUANTITY && quantity <= MAX_QUANTITY;
}

/** "3 of 10 files · max 100 MB each"; without the parts the config doesn't know yet. */
export function limitsText(count: number, config: UploadConfig): string {
  const parts: string[] = [];
  if (config.maxFiles > 0) {
    parts.push(`${count} of ${config.maxFiles} files`);
  }
  if (config.maxSizeBytes > 0) {
    parts.push(`max ${formatBytes(config.maxSizeBytes)} each`);
  }
  return parts.join(' · ');
}

/** The message for a failed upload. */
export function uploadErrorMessage(err: unknown, config: UploadConfig, fallback: string): string {
  // 413 comes from the HTTP layer before the app sees the request, so it has no message body.
  if (err instanceof HttpErrorResponse && err.status === 413) {
    if (config.maxSizeBytes > 0) {
      return `The file is larger than ${formatBytes(config.maxSizeBytes)}`;
    }
    return 'The file is too large';
  }
  return errorMessage(err, fallback);
}
