import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Settings } from '../../core/models/settings.model';

/** Admin settings API. */
@Injectable({ providedIn: 'root' })
export class SettingsService {
  private readonly http = inject(HttpClient);

  get(): Observable<Settings> {
    return this.http.get<Settings>('/api/settings');
  }

  update(settings: Settings): Observable<Settings> {
    return this.http.put<Settings>('/api/settings', settings);
  }
}
