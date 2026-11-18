import { inject, Injectable } from '@angular/core';
import { catchError, concat, defer, map, Observable, of, takeWhile, tap } from 'rxjs';
import { NewEntry, PrintRequest } from '../../core/models/print-request.model';
import { PrintRequestsService, UploadEvent } from './print-requests.service';

/** What happens to the entries of a batch; `index` is the entry's position in the batch. */
export type BatchUploadEvent =
  | { type: 'started'; index: number }
  | { type: 'progress'; index: number; percent: number }
  | { type: 'uploaded'; index: number; request: PrintRequest }
  | { type: 'failed'; index: number; error: unknown };

/**
 * Adds model files and links (#59) to a request one at a time (#23), so one that fails doesn't
 * lose the rest. Unsubscribing cancels the upload that is running and skips the entries after it.
 */
@Injectable({ providedIn: 'root' })
export class RequestUploadService {
  private readonly requestsService = inject(PrintRequestsService);

  /**
   * Creates the request with the first entry, then adds the others to it. The stream errors when
   * the first entry fails, since there is no request then; a later one that fails is reported
   * as `failed` and the next one goes on.
   */
  createWithEntries(
    name: string,
    description: string,
    entries: NewEntry[],
  ): Observable<BatchUploadEvent> {
    return defer(() => {
      const [first, ...others] = entries;
      let requestId: number | null = null;
      const creation = this.requestsService.create({ name, description, first });
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
          return track(position + 1, this.add(requestId, other)).pipe(
            catchError(failedAt(position + 1)),
          );
        });
      });
      return concat(created, ...added);
    });
  }

  /** Adds entries to an existing request; one that fails is reported as `failed`. */
  addEntries(requestId: number, entries: NewEntry[]): Observable<BatchUploadEvent> {
    const added = entries.map((entry, index) => {
      return defer(() => {
        return track(index, this.add(requestId, entry)).pipe(catchError(failedAt(index)));
      });
    });
    return concat(...added);
  }

  /** A file is uploaded with progress; a link is one small request without any. */
  private add(requestId: number, entry: NewEntry): Observable<UploadEvent> {
    if (entry.kind === 'file') {
      return this.requestsService.addFile(requestId, entry.file, entry.quantity);
    }
    return this.requestsService.addLink(requestId, entry.link, entry.quantity).pipe(
      map((request): UploadEvent => {
        return { type: 'done', request };
      }),
    );
  }
}

/** One entry's upload as batch events: started, its progress, then uploaded. */
function track(index: number, upload: Observable<UploadEvent>): Observable<BatchUploadEvent> {
  const events = upload.pipe(
    // The request the upload answers with ends this entry, whether or not the source completes.
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
