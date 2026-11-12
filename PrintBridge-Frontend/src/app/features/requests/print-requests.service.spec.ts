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

  it('lists all requests with status filter and trimmed search', () => {
    service.listAll('OPEN', '  ben ').subscribe();
    const search = http.expectOne((req) => {
      return req.url === '/api/requests/all';
    });
    expect(search.request.params.get('status')).toBe('OPEN');
    expect(search.request.params.get('q')).toBe('ben');
    search.flush([REQUEST_SUMMARY]);

    service.listAll('ALL', ' ').subscribe();
    const all = http.expectOne((req) => {
      return req.url === '/api/requests/all';
    });
    expect(all.request.params.get('status')).toBe('ALL');
    expect(all.request.params.has('q')).toBe(false);
    all.flush([]);
  });

  it('changes status and price', () => {
    service.changeStatus(4, 'DECLINED', 'Too big').subscribe();
    const status = http.expectOne({ method: 'PUT', url: '/api/requests/4/status' });
    expect(status.request.body).toEqual({ status: 'DECLINED', declineReason: 'Too big' });
    status.flush(REQUEST);

    service.changeStatus(4, 'IN_REVIEW').subscribe();
    const review = http.expectOne({ method: 'PUT', url: '/api/requests/4/status' });
    expect(review.request.body).toEqual({ status: 'IN_REVIEW', declineReason: null });
    review.flush(REQUEST);

    service.changePrice(4, 12.5).subscribe();
    const price = http.expectOne({ method: 'PUT', url: '/api/requests/4/price' });
    expect(price.request.body).toEqual({ finalPrice: 12.5 });
    price.flush(REQUEST);

    service.changePrice(4, null).subscribe();
    const clear = http.expectOne({ method: 'PUT', url: '/api/requests/4/price' });
    expect(clear.request.body).toEqual({ finalPrice: null });
    clear.flush(REQUEST);

    service.changePaid(4, true).subscribe();
    const paid = http.expectOne({ method: 'PUT', url: '/api/requests/4/paid' });
    expect(paid.request.body).toEqual({ paid: true });
    paid.flush(REQUEST);

    service.reslice(4).subscribe();
    http.expectOne({ method: 'POST', url: '/api/requests/4/reslice' }).flush(REQUEST);
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
