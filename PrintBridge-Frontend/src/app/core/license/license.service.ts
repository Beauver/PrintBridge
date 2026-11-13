import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { License } from '../models/license.model';

/** Public, also before logging in. */
@Injectable({ providedIn: 'root' })
export class LicenseService {
  private readonly http = inject(HttpClient);

  get(): Observable<License> {
    return this.http.get<License>('/api/license');
  }
}
