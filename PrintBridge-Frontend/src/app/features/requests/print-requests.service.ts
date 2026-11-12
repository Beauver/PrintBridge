import { HttpClient, HttpEventType } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { filter, map, Observable } from 'rxjs';
import {
  NewPrintRequest,
  PrintRequest,
  PrintRequestSummary,
  UploadConfig,
} from '../../core/models';

/** Progress of an upload: percentages while sending, then the created request. */
export type UploadEvent =
  { type: 'progress'; percent: number } | { type: 'done'; request: PrintRequest };

/** Print requests API. */
@Injectable({ providedIn: 'root' })
export class PrintRequestsService {
  private readonly http = inject(HttpClient);

  // TODO: #5 add listAll(filters) for the admin overview.
  listOwn(): Observable<PrintRequestSummary[]> {
    return this.http.get<PrintRequestSummary[]>('/api/requests');
  }

  get(id: number): Observable<PrintRequest> {
    return this.http.get<PrintRequest>(`/api/requests/${id}`);
  }

  uploadConfig(): Observable<UploadConfig> {
    return this.http.get<UploadConfig>('/api/requests/upload-config');
  }

  create(request: NewPrintRequest): Observable<UploadEvent> {
    const body = new FormData();
    body.append('name', request.name);
    body.append('description', request.description);
    body.append('file', request.file, request.file.name);
    return this.http
      .post<PrintRequest>('/api/requests', body, { reportProgress: true, observe: 'events' })
      .pipe(
        map((event): UploadEvent | null => {
          if (event.type === HttpEventType.UploadProgress) {
            const total = event.total ?? request.file.size;
            return {
              type: 'progress',
              percent: total ? Math.round((100 * event.loaded) / total) : 0,
            };
          }
          if (event.type === HttpEventType.Response && event.body) {
            return { type: 'done', request: event.body };
          }
          return null;
        }),
        filter((event): event is UploadEvent => event !== null),
      );
  }

  downloadFile(requestId: number, fileId: number): Observable<Blob> {
    return this.http.get(`/api/requests/${requestId}/files/${fileId}`, { responseType: 'blob' });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/requests/${id}`);
  }
}
