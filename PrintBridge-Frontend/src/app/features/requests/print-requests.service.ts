import { HttpClient, HttpEventType, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { filter, map, Observable } from 'rxjs';
import {
  ItemStatus,
  NewLink,
  NewPrintRequest,
  PrintRequest,
  PrintRequestSummary,
  RequestStatus,
  StatusFilter,
  UploadConfig,
} from '../../core/models/print-request.model';
import { ResliceChoice, ResliceOptions } from '../../core/models/slicer.model';
import { FilamentChoiceInput } from '../../core/models/spoolman.model';

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

  /** Change the name and description (#84); an empty description clears it. */
  changeDetails(id: number, name: string, description: string): Observable<PrintRequest> {
    return this.http.put<PrintRequest>(`/api/requests/${id}`, { name, description });
  }

  /** Admin: set the final price in the configured currency, or clear it with `null`. */
  changePrice(id: number, finalPrice: number | null): Observable<PrintRequest> {
    return this.http.put<PrintRequest>(`/api/requests/${id}/price`, { finalPrice });
  }

  get(id: number): Observable<PrintRequest> {
    return this.http.get<PrintRequest>(`/api/requests/${id}`);
  }

  uploadConfig(): Observable<UploadConfig> {
    return this.http.get<UploadConfig>('/api/requests/upload-config');
  }

  /**
   * Creates the request with its first file or link (#59); add the others with `addFile` and
   * `addLink`.
   */
  create(request: NewPrintRequest): Observable<UploadEvent> {
    const first = request.first;
    const body = new FormData();
    body.append('name', request.name);
    body.append('description', request.description);
    body.append('quantity', String(first.quantity));
    if (first.kind === 'file') {
      body.append('file', first.file, first.file.name);
      return this.upload('/api/requests', body, first.file.size);
    }
    body.append('url', first.link.url.trim());
    if (first.link.name.trim()) {
      body.append('linkName', first.link.name.trim());
    }
    return this.upload('/api/requests', body, 0);
  }

  /** Adds a model file to a request; answers with the whole request. */
  addFile(requestId: number, file: File, quantity: number): Observable<UploadEvent> {
    const body = new FormData();
    body.append('quantity', String(quantity));
    body.append('file', file, file.name);
    return this.upload(`/api/requests/${requestId}/files`, body, file.size);
  }

  /** Adds a link to a request (#59); answers with the whole request. */
  addLink(requestId: number, link: NewLink, quantity: number): Observable<PrintRequest> {
    let name: string | null = null;
    if (link.name.trim()) {
      name = link.name.trim();
    }
    return this.http.post<PrintRequest>(`/api/requests/${requestId}/links`, {
      url: link.url.trim(),
      name,
      quantity,
    });
  }

  removeFile(requestId: number, fileId: number): Observable<PrintRequest> {
    return this.http.delete<PrintRequest>(`/api/requests/${requestId}/files/${fileId}`);
  }

  changeQuantity(requestId: number, fileId: number, quantity: number): Observable<PrintRequest> {
    return this.http.put<PrintRequest>(`/api/requests/${requestId}/files/${fileId}/quantity`, {
      quantity,
    });
  }

  /** Chooses a Spoolman filament for one colour of a plate, or for the whole item (#11). */
  chooseFilament(
    requestId: number,
    fileId: number,
    choice: FilamentChoiceInput,
  ): Observable<PrintRequest> {
    return this.http.put<PrintRequest>(
      `/api/requests/${requestId}/files/${fileId}/filaments`,
      choice,
    );
  }

  /** Removes a chosen filament; the colour goes back to the file's own. */
  removeFilamentChoice(
    requestId: number,
    fileId: number,
    choiceId: number,
  ): Observable<PrintRequest> {
    return this.http.delete<PrintRequest>(
      `/api/requests/${requestId}/files/${fileId}/filaments/${choiceId}`,
    );
  }

  /** Every file of the request in one ZIP, with its links in `links.txt`. */
  downloadAll(requestId: number): Observable<Blob> {
    return this.http.get(`/api/requests/${requestId}/files.zip`, { responseType: 'blob' });
  }

  /**
   * Posts a multipart upload and reports its progress, then the request it answers with.
   * `size` is used when the browser doesn't know the total.
   */
  private upload(url: string, body: FormData, size: number): Observable<UploadEvent> {
    return this.http
      .post<PrintRequest>(url, body, { reportProgress: true, observe: 'events' })
      .pipe(
        map((event): UploadEvent | null => {
          if (event.type === HttpEventType.UploadProgress) {
            const total = event.total ?? size;
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

  /** Admin: move one file or link to another status (#61); the reason only counts when declining. */
  changeItemStatus(
    requestId: number,
    fileId: number,
    status: ItemStatus,
    declineReason: string | null = null,
  ): Observable<PrintRequest> {
    return this.http.put<PrintRequest>(`/api/requests/${requestId}/files/${fileId}/status`, {
      status,
      declineReason,
    });
  }

  /** Admin: what the "Re-slice with…" dialog of one file offers (#21). */
  resliceOptions(requestId: number, fileId: number): Observable<ResliceOptions> {
    return this.http.get<ResliceOptions>(`/api/requests/${requestId}/files/${fileId}/reslice`);
  }

  /** Admin: slice one file again with a chosen printer, nozzle and profiles (#21). */
  resliceWith(requestId: number, fileId: number, choice: ResliceChoice): Observable<PrintRequest> {
    return this.http.post<PrintRequest>(
      `/api/requests/${requestId}/files/${fileId}/reslice`,
      choice,
    );
  }

  downloadFile(requestId: number, fileId: number): Observable<Blob> {
    return this.http.get(`/api/requests/${requestId}/files/${fileId}`, { responseType: 'blob' });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/requests/${id}`);
  }
}
