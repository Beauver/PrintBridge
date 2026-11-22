import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  PrinterInput,
  Settings,
  SlicingSettings,
  SpoolmanSettings,
  UploadSettings,
} from '../../core/models/settings.model';
import { SlicerPrinter, SlicerProfiles, SlicerStatus } from '../../core/models/slicer.model';
import { SpoolmanStatus } from '../../core/models/spoolman.model';

/** Admin settings API, plus what the slicer service offers for them. Changes answer with all settings. */
@Injectable({ providedIn: 'root' })
export class SettingsService {
  private readonly http = inject(HttpClient);

  get(): Observable<Settings> {
    return this.http.get<Settings>('/api/settings');
  }

  updateSlicing(slicing: SlicingSettings): Observable<Settings> {
    return this.http.put<Settings>('/api/settings/slicing', slicing);
  }

  updateUploads(uploads: UploadSettings): Observable<Settings> {
    return this.http.put<Settings>('/api/settings/uploads', uploads);
  }

  updateSpoolman(spoolman: SpoolmanSettings): Observable<Settings> {
    return this.http.put<Settings>('/api/settings/spoolman', spoolman);
  }

  spoolmanStatus(): Observable<SpoolmanStatus> {
    return this.http.get<SpoolmanStatus>('/api/spoolman/status');
  }

  addPrinter(printer: PrinterInput): Observable<Settings> {
    return this.http.post<Settings>('/api/settings/printers', printer);
  }

  updatePrinter(id: number, printer: PrinterInput): Observable<Settings> {
    return this.http.put<Settings>(`/api/settings/printers/${id}`, printer);
  }

  deletePrinter(id: number): Observable<Settings> {
    return this.http.delete<Settings>(`/api/settings/printers/${id}`);
  }

  makeDefaultPrinter(id: number): Observable<Settings> {
    return this.http.put<Settings>(`/api/settings/printers/${id}/default`, null);
  }

  slicerStatus(): Observable<SlicerStatus> {
    return this.http.get<SlicerStatus>('/api/slicer/status');
  }

  slicerPrinters(): Observable<SlicerPrinter[]> {
    return this.http.get<SlicerPrinter[]>('/api/slicer/printers');
  }

  profiles(printer: string, nozzle: number): Observable<SlicerProfiles> {
    const params = new HttpParams().set('printer', printer).set('nozzle', String(nozzle));
    return this.http.get<SlicerProfiles>('/api/slicer/profiles', { params });
  }
}
