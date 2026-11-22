import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SPOOLMAN_RED } from '../../../testing/fixtures';
import { LOAD_FAILED, SpoolmanFilamentsService } from './spoolman-filaments.service';

describe('SpoolmanFilamentsService', () => {
  let service: SpoolmanFilamentsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [SpoolmanFilamentsService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SpoolmanFilamentsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('loads the list once unless forced', () => {
    service.load();
    expect(service.loading()).toBe(true);
    // Already loading: no second request.
    service.load();
    http.expectOne({ method: 'GET', url: '/api/spoolman/filaments' }).flush([SPOOLMAN_RED]);
    expect(service.filaments()).toEqual([SPOOLMAN_RED]);
    expect(service.loading()).toBe(false);

    service.load();
    http.expectNone('/api/spoolman/filaments');

    service.load(true);
    http.expectOne('/api/spoolman/filaments').flush([]);
    expect(service.filaments()).toEqual([]);
  });

  it('says Spoolman could not be read, or why the list is refused', () => {
    service.load();
    http
      .expectOne('/api/spoolman/filaments')
      .flush({ message: "Spoolman can't be reached" }, { status: 503, statusText: 'Down' });
    expect(service.error()).toBe(LOAD_FAILED);
    expect(service.filaments()).toBeNull();

    service.load();
    http.expectOne('/api/spoolman/filaments').flush(
      { message: 'Choosing filaments from Spoolman is switched off' },
      {
        status: 409,
        statusText: 'Conflict',
      },
    );
    expect(service.error()).toBe('Choosing filaments from Spoolman is switched off');
  });
});
