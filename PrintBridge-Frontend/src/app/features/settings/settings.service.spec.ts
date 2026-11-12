import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SettingsService } from './settings.service';

describe('SettingsService', () => {
  let service: SettingsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SettingsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('calls the settings API', () => {
    const settings = { buildPlateX: 256, buildPlateY: 256, buildPlateZ: 250 };

    service.get().subscribe();
    http.expectOne({ method: 'GET', url: '/api/settings' }).flush(settings);

    service.update(settings).subscribe();
    const update = http.expectOne({ method: 'PUT', url: '/api/settings' });
    expect(update.request.body).toEqual(settings);
    update.flush(settings);
  });
});
