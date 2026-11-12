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
    service.get().subscribe();
    http.expectOne({ method: 'GET', url: '/api/settings' }).flush({});

    const buildPlate = { x: 256, y: 256, z: 250 };
    service.updateBuildPlate(buildPlate).subscribe();
    const plate = http.expectOne({ method: 'PUT', url: '/api/settings/build-plate' });
    expect(plate.request.body).toEqual(buildPlate);
    plate.flush({});

    const slicing = {
      enabled: true,
      timeoutMinutes: 10,
      printer: 'P1S',
      ownedNozzles: [0.4],
      defaultNozzle: 0.4,
      process: 'p',
      filament: 'f',
    };
    service.updateSlicing(slicing).subscribe();
    const update = http.expectOne({ method: 'PUT', url: '/api/settings/slicing' });
    expect(update.request.body).toEqual(slicing);
    update.flush({});
  });

  it('calls the slicer API', () => {
    service.slicerStatus().subscribe();
    http.expectOne({ method: 'GET', url: '/api/slicer/status' }).flush({});

    service.printers().subscribe();
    http.expectOne({ method: 'GET', url: '/api/slicer/printers' }).flush([]);

    service.profiles('Bambu Lab P1S', 0.4).subscribe();
    const profiles = http.expectOne((request) => {
      return request.url === '/api/slicer/profiles';
    });
    expect(profiles.request.params.get('printer')).toBe('Bambu Lab P1S');
    expect(profiles.request.params.get('nozzle')).toBe('0.4');
    profiles.flush({});
  });
});
