import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, map, Observable, throwError } from 'rxjs';
import { PreviewMesh } from '../../core/models/model-preview.model';
import { decodeMesh } from '../../shared/model-viewer/mesh-decoder';

/** 3D preview meshes and plate images of model files (#13). */
@Injectable({ providedIn: 'root' })
export class ModelPreviewService {
  private readonly http = inject(HttpClient);

  /** The mesh of an uploaded file. */
  fileMesh(requestId: number, fileId: number): Observable<PreviewMesh> {
    return this.http
      .get(`/api/requests/${requestId}/files/${fileId}/preview`, { responseType: 'arraybuffer' })
      .pipe(map(decodeMesh), catchError(readErrorBody));
  }

  /** The mesh of a file that isn't uploaded yet; the backend doesn't keep it. */
  uploadMesh(file: File): Observable<PreviewMesh> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http
      .post('/api/previews', body, { responseType: 'arraybuffer' })
      .pipe(map(decodeMesh), catchError(readErrorBody));
  }

  /** The image Bambu Studio / OrcaSlicer saved of a plate; errors with 404 when there is none. */
  thumbnail(requestId: number, fileId: number, plate: number): Observable<Blob> {
    return this.http.get(`/api/requests/${requestId}/files/${fileId}/plates/${plate}/thumbnail`, {
      responseType: 'blob',
    });
  }
}

/**
 * Binary requests get their error body as bytes too; turn the backend's JSON message back into
 * an object, so `errorMessage` can show it.
 */
function readErrorBody(error: unknown): Observable<never> {
  if (!(error instanceof HttpErrorResponse) || !(error.error instanceof ArrayBuffer)) {
    return throwError(() => {
      return error;
    });
  }
  let body: unknown = null;
  try {
    body = JSON.parse(new TextDecoder().decode(error.error));
  } catch {
    // Not JSON: keep no body, the caller shows its fallback text.
  }
  const readable = new HttpErrorResponse({
    error: body,
    headers: error.headers,
    status: error.status,
    statusText: error.statusText,
    url: error.url ?? undefined,
  });
  return throwError(() => {
    return readable;
  });
}
