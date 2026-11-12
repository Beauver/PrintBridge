import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BuildPlate, Settings, SlicingSettings } from '../../core/models/settings.model';
import { SlicerPrinter, SlicerProfiles, SlicerStatus } from '../../core/models/slicer.model';

/** Admin settings API, plus what the slicer service offers for them. */
@Injectable({ providedIn: 'root' })
export class SettingsService {
  private readonly http = inject(HttpClient);

  get(): Observable<Settings> {
    return this.http.get<Settings>('/api/settings');
  }

  updateBuildPlate(buildPlate: BuildPlate): Observable<Settings> {
    return this.http.put<Settings>('/api/settings/build-plate', buildPlate);
  }

  updateSlicing(slicing: SlicingSettings): Observable<Settings> {
    return this.http.put<Settings>('/api/settings/slicing', slicing);
  }

  slicerStatus(): Observable<SlicerStatus> {
    return this.http.get<SlicerStatus>('/api/slicer/status');
  }

  printers(): Observable<SlicerPrinter[]> {
    return this.http.get<SlicerPrinter[]>('/api/slicer/printers');
  }

  profiles(printer: string, nozzle: number): Observable<SlicerProfiles> {
    const params = new HttpParams().set('printer', printer).set('nozzle', String(nozzle));
    return this.http.get<SlicerProfiles>('/api/slicer/profiles', { params });
  }
}
