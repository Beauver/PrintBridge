import { HttpEventType, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { REQUEST, REQUEST_SUMMARY } from '../../core/testing';
import { PrintRequestsService, UploadEvent } from './print-requests.service';

describe('PrintRequestsService', () => {
  let service: PrintRequestsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PrintRequestsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('calls the read and delete endpoints', () => {
    service.listOwn().subscribe((list) => {
      expect(list).toEqual([REQUEST_SUMMARY]);
    });
    http.expectOne({ method: 'GET', url: '/api/requests' }).flush([REQUEST_SUMMARY]);

    service.get(4).subscribe((request) => {
      expect(request).toEqual(REQUEST);
    });
    http.expectOne({ method: 'GET', url: '/api/requests/4' }).flush(REQUEST);

    service.uploadConfig().subscribe();
    http
      .expectOne({ method: 'GET', url: '/api/requests/upload-config' })
      .flush({ maxSizeBytes: 1, allowedExtensions: ['stl'] });

    service.delete(4).subscribe();
    http.expectOne({ method: 'DELETE', url: '/api/requests/4' }).flush(null);
  });

  it('downloads a file as a blob', () => {
    service.downloadFile(4, 7).subscribe((blob) => {
      expect(blob).toBeInstanceOf(Blob);
    });
    const req = http.expectOne({ method: 'GET', url: '/api/requests/4/files/7' });
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['x']));
  });

  it('uploads as multipart and reports progress then the created request', () => {
    const file = new File(['solid'], 'cube.stl');
    const events: UploadEvent[] = [];
    service.create({ name: 'Cube', description: 'Small', file }).subscribe((event) => {
      events.push(event);
    });

    const req = http.expectOne({ method: 'POST', url: '/api/requests' });
    const body = req.request.body as FormData;
    expect(body.get('name')).toBe('Cube');
    expect(body.get('description')).toBe('Small');
    expect((body.get('file') as File).name).toBe('cube.stl');
    expect(req.request.reportProgress).toBe(true);

    req.event({ type: HttpEventType.Sent });
    req.event({ type: HttpEventType.UploadProgress, loaded: 50, total: 200 });
    req.event({ type: HttpEventType.UploadProgress, loaded: 5 });
    req.flush(REQUEST);

    expect(events).toEqual([
      { type: 'progress', percent: 25 },
      { type: 'progress', percent: 100 },
      { type: 'done', request: REQUEST },
    ]);
  });

  it('reports 0% when the total size is unknown', () => {
    const events: UploadEvent[] = [];
    service
      .create({ name: 'Empty', description: '', file: new File([], 'a.stl') })
      .subscribe((event) => {
        events.push(event);
      });
    const req = http.expectOne('/api/requests');
    req.event({ type: HttpEventType.UploadProgress, loaded: 0 });
    req.flush(REQUEST);
    expect(events[0]).toEqual({ type: 'progress', percent: 0 });
  });
});
