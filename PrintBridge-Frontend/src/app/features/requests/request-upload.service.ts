import { inject, Injectable } from '@angular/core';
import { catchError, concat, defer, map, Observable, of, takeWhile, tap } from 'rxjs';
import { PrintRequest } from '../../core/models/print-request.model';
import { PrintRequestsService, UploadEvent } from './print-requests.service';

/** A file to upload with its number of copies. */
export interface FileToUpload {
  file: File;
  quantity: number;
}

/** What happens to the files of a batch; `index` is the file's position in the batch. */
export type BatchUploadEvent =
  | { type: 'started'; index: number }
  | { type: 'progress'; index: number; percent: number }
  | { type: 'uploaded'; index: number; request: PrintRequest }
  | { type: 'failed'; index: number; error: unknown };

/**
 * Uploads several model files one at a time (#23), so one that fails doesn't lose the rest.
 * Unsubscribing cancels the upload that is running and skips the files after it.
 */
@Injectable({ providedIn: 'root' })
export class RequestUploadService {
  private readonly requestsService = inject(PrintRequestsService);

  /**
   * Creates the request with the first file, then adds the others to it. The stream errors when
   * the first file fails, since there is no request then; a later file that fails is reported
   * as `failed` and the next one goes on.
   */
  createWithFiles(
    name: string,
    description: string,
    files: FileToUpload[],
  ): Observable<BatchUploadEvent> {
    return defer(() => {
      const [first, ...others] = files;
      let requestId: number | null = null;
      const creation = this.requestsService.create({
        name,
        description,
        file: first.file,
        quantity: first.quantity,
      });
      const created = track(0, creation).pipe(
        tap((event) => {
          if (event.type === 'uploaded') {
            requestId = event.request.id;
          }
        }),
      );
      const added = others.map((other, position) => {
        return defer(() => {
          if (requestId === null) {
            throw new Error('The request was not created');
          }
          const addition = this.requestsService.addFile(requestId, other.file, other.quantity);
          return track(position + 1, addition).pipe(catchError(failedAt(position + 1)));
        });
      });
      return concat(created, ...added);
    });
  }

  /** Adds files to an existing request; a file that fails is reported as `failed`. */
  addFiles(requestId: number, files: FileToUpload[]): Observable<BatchUploadEvent> {
    const added = files.map((file, index) => {
      return defer(() => {
        const addition = this.requestsService.addFile(requestId, file.file, file.quantity);
        return track(index, addition).pipe(catchError(failedAt(index)));
      });
    });
    return concat(...added);
  }
}

/** One file's upload as batch events: started, its progress, then uploaded. */
function track(index: number, upload: Observable<UploadEvent>): Observable<BatchUploadEvent> {
  const events = upload.pipe(
    // The request the upload answers with ends this file, whether or not the source completes.
    takeWhile((event) => {
      return event.type !== 'done';
    }, true),
    map((event): BatchUploadEvent => {
      if (event.type === 'progress') {
        return { type: 'progress', index, percent: event.percent };
      }
      return { type: 'uploaded', index, request: event.request };
    }),
  );
  return concat(of<BatchUploadEvent>({ type: 'started', index }), events);
}

function failedAt(index: number): (error: unknown) => Observable<BatchUploadEvent> {
  return (error: unknown) => {
    return of<BatchUploadEvent>({ type: 'failed', index, error });
  };
}
