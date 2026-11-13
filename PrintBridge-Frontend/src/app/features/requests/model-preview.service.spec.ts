import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { errorMessage } from '../../core/http/api-error';
import { PreviewMesh } from '../../core/models/model-preview.model';
import { encodeMesh, TRIANGLE_MESH } from '../../core/testing';
import { ModelPreviewService } from './model-preview.service';

describe('ModelPreviewService', () => {
  let service: ModelPreviewService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ModelPreviewService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  /** A plain ArrayBuffer of this realm (TextEncoder's comes from jsdom's). */
  function bytesOf(text: string): ArrayBuffer {
    const encoded = new TextEncoder().encode(text);
    const buffer = new ArrayBuffer(encoded.length);
    new Uint8Array(buffer).set(encoded);
    return buffer;
  }

  it('loads and decodes the mesh of an uploaded file', () => {
    let mesh: PreviewMesh | null = null;
    service.fileMesh(4, 7).subscribe((loaded) => {
      mesh = loaded;
    });

    const request = http.expectOne({ method: 'GET', url: '/api/requests/4/files/7/preview' });
    expect(request.request.responseType).toBe('arraybuffer');
    request.flush(encodeMesh(TRIANGLE_MESH));

    expect(mesh!.plates).toEqual(TRIANGLE_MESH.plates);
  });

  it('sends a chosen file for a preview before uploading', () => {
    const file = new File(['solid'], 'cube.stl');
    let mesh: PreviewMesh | null = null;
    service.uploadMesh(file).subscribe((loaded) => {
      mesh = loaded;
    });

    const request = http.expectOne({ method: 'POST', url: '/api/previews' });
    expect((request.request.body as FormData).get('file')).toBeInstanceOf(File);
    request.flush(encodeMesh(TRIANGLE_MESH));

    expect(mesh!.parts).toHaveLength(1);
  });

  it('turns the binary error body back into the backend message', () => {
    let error: unknown = null;
    service.fileMesh(4, 7).subscribe({
      error: (err: unknown) => {
        error = err;
      },
    });

    http
      .expectOne('/api/requests/4/files/7/preview')
      .flush(bytesOf(JSON.stringify({ message: 'This model is too detailed for a preview' })), {
        status: 422,
        statusText: 'Unprocessable',
      });

    expect(errorMessage(error)).toBe('This model is too detailed for a preview');
  });

  it('keeps errors without a readable body', () => {
    const errors: unknown[] = [];
    service.uploadMesh(new File(['x'], 'x.stl')).subscribe({
      error: (err: unknown) => {
        errors.push(err);
      },
    });
    http
      .expectOne('/api/previews')
      .flush(bytesOf('<html>'), { status: 502, statusText: 'Bad Gateway' });
    service.fileMesh(1, 2).subscribe({
      error: (err: unknown) => {
        errors.push(err);
      },
    });
    http.expectOne('/api/requests/1/files/2/preview').error(new ProgressEvent('error'));

    expect(errorMessage(errors[0], 'fallback')).toBe('fallback');
    expect(errorMessage(errors[1])).toBe('Cannot reach the server.');
  });

  it('fails on a body that is not a mesh', () => {
    let error: unknown = null;
    service.fileMesh(4, 7).subscribe({
      error: (err: unknown) => {
        error = err;
      },
    });

    http.expectOne('/api/requests/4/files/7/preview').flush(new ArrayBuffer(2));

    expect(error).toBeInstanceOf(Error);
  });

  it('loads plate images as blobs', () => {
    service.thumbnail(4, 7, 2).subscribe();

    const request = http.expectOne('/api/requests/4/files/7/plates/2/thumbnail');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['png']));
  });
});
