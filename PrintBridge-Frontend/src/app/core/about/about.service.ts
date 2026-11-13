import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { About } from '../models/about.model';

/** Public, also before logging in. */
@Injectable({ providedIn: 'root' })
export class AboutService {
  private readonly http = inject(HttpClient);

  get(): Observable<About> {
    return this.http.get<About>('/api/about');
  }
}
