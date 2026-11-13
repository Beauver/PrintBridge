import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AboutService } from './about.service';

describe('AboutService', () => {
  it('loads the public about info', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpTestingController);

    TestBed.inject(AboutService).get().subscribe();

    http.expectOne({ method: 'GET', url: '/api/about' }).flush({});
    http.verify();
  });
});
