import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { LicenseService } from './license.service';

describe('LicenseService', () => {
  it('loads the public licence information', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpTestingController);

    TestBed.inject(LicenseService).get().subscribe();

    http.expectOne({ method: 'GET', url: '/api/license' }).flush({});
    http.verify();
  });
});
