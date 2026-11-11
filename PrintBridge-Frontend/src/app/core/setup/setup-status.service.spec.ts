import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SetupStatusService } from './setup-status.service';

describe('SetupStatusService', () => {
  let service: SetupStatusService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SetupStatusService);
    http = TestBed.inject(HttpTestingController);
  });

  it('loads whether setup is required', async () => {
    const loaded = service.load();
    http.expectOne('/api/setup').flush({ setupRequired: true });
    await loaded;
    expect(service.required()).toBe(true);

    service.markDone();
    expect(service.required()).toBe(false);
  });

  it('assumes setup is done when the backend is unreachable', async () => {
    const loaded = service.load();
    http.expectOne('/api/setup').error(new ProgressEvent('error'));
    await loaded;
    expect(service.required()).toBe(false);
  });
});
