import { HttpClient, HttpEventType, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { filter, map, Observable } from 'rxjs';
import {
  NewPrintRequest,
  PrintRequest,
  PrintRequestSummary,
  RequestStatus,
  StatusFilter,
  UploadConfig,
} from '../../core/models/print-request.model';

/** Progress of an upload: percentages while sending, then the created request. */
export type UploadEvent =
  { type: 'progress'; percent: number } | { type: 'done'; request: PrintRequest };

/** Print requests API. */
@Injectable({ providedIn: 'root' })
export class PrintRequestsService {
  private readonly http = inject(HttpClient);

  listOwn(): Observable<PrintRequestSummary[]> {
    return this.http.get<PrintRequestSummary[]>('/api/requests');
  }

  /** Admin overview: everyone's requests, filtered by status and searched by name/username. */
  listAll(status: StatusFilter, search: string): Observable<PrintRequestSummary[]> {
    let params = new HttpParams().set('status', status);
    if (search.trim()) {
      params = params.set('q', search.trim());
    }
    return this.http.get<PrintRequestSummary[]>('/api/requests/all', { params });
  }

  /** Admin: move a request to another status. `declineReason` is only used when declining. */
  changeStatus(
    id: number,
    status: RequestStatus,
    declineReason: string | null = null,
  ): Observable<PrintRequest> {
    return this.http.put<PrintRequest>(`/api/requests/${id}/status`, { status, declineReason });
  }

  /** Admin: set the final price in EUR, or clear it with `null`. */
  changePrice(id: number, finalPrice: number | null): Observable<PrintRequest> {
    return this.http.put<PrintRequest>(`/api/requests/${id}/price`, { finalPrice });
  }

  get(id: number): Observable<PrintRequest> {
    return this.http.get<PrintRequest>(`/api/requests/${id}`);
  }

  uploadConfig(): Observable<UploadConfig> {
    return this.http.get<UploadConfig>('/api/requests/upload-config');
  }

  /** Creates the request with its first file; add the others with `addFile`. */
  create(request: NewPrintRequest): Observable<UploadEvent> {
    const body = new FormData();
    body.append('name', request.name);
    body.append('description', request.description);
    body.append('quantity', String(request.quantity));
    body.append('file', request.file, request.file.name);
    return this.upload('/api/requests', body, request.file);
  }

  /** Adds a model file to a request; answers with the whole request. */
  addFile(requestId: number, file: File, quantity: number): Observable<UploadEvent> {
    const body = new FormData();
    body.append('quantity', String(quantity));
    body.append('file', file, file.name);
    return this.upload(`/api/requests/${requestId}/files`, body, file);
  }

  removeFile(requestId: number, fileId: number): Observable<PrintRequest> {
    return this.http.delete<PrintRequest>(`/api/requests/${requestId}/files/${fileId}`);
  }

  changeQuantity(requestId: number, fileId: number, quantity: number): Observable<PrintRequest> {
    return this.http.put<PrintRequest>(`/api/requests/${requestId}/files/${fileId}/quantity`, {
      quantity,
    });
  }

  /** Every file of the request in one ZIP. */
  downloadAll(requestId: number): Observable<Blob> {
    return this.http.get(`/api/requests/${requestId}/files.zip`, { responseType: 'blob' });
  }

  /** Posts a multipart upload and reports its progress, then the request it answers with. */
  private upload(url: string, body: FormData, file: File): Observable<UploadEvent> {
    return this.http
      .post<PrintRequest>(url, body, { reportProgress: true, observe: 'events' })
      .pipe(
        map((event): UploadEvent | null => {
          if (event.type === HttpEventType.UploadProgress) {
            const total = event.total ?? file.size;
            let percent = 0;
            if (total > 0) {
              percent = Math.round((100 * event.loaded) / total);
            }
            return { type: 'progress', percent };
          }
          if (event.type === HttpEventType.Response && event.body) {
            return { type: 'done', request: event.body };
          }
          return null;
        }),
        filter((event): event is UploadEvent => {
          return event !== null;
        }),
      );
  }

  /** Admin: mark an accepted/completed request with a price as paid, or undo that. */
  changePaid(id: number, paid: boolean): Observable<PrintRequest> {
    return this.http.put<PrintRequest>(`/api/requests/${id}/paid`, { paid });
  }

  /** Admin: slice one file of the request again on the server. */
  reslice(requestId: number, fileId: number): Observable<PrintRequest> {
    return this.http.post<PrintRequest>(`/api/requests/${requestId}/files/${fileId}/reslice`, null);
  }

  downloadFile(requestId: number, fileId: number): Observable<Blob> {
    return this.http.get(`/api/requests/${requestId}/files/${fileId}`, { responseType: 'blob' });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/requests/${id}`);
  }
}
