import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { errorMessage } from '../../core/http/api-error';
import { SpoolmanFilament } from '../../core/models/spoolman.model';

export const LOAD_FAILED = "Couldn't load filaments from Spoolman";

/**
 * The filaments a requester can choose from Spoolman (#11). Provided by the request page, so the
 * list is loaded once per visit and shared by every circle on it; the backend caches it too.
 */
@Injectable()
export class SpoolmanFilamentsService {
  private readonly http = inject(HttpClient);

  /** Null until loaded. */
  readonly filaments = signal<SpoolmanFilament[] | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  /** Loads the list unless it is loaded or loading; `force` asks again (after a failed choice). */
  load(force = false): void {
    if (this.loading() || (this.filaments() !== null && !force)) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.http.get<SpoolmanFilament[]>('/api/spoolman/filaments').subscribe({
      next: (filaments) => {
        this.filaments.set(filaments);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        // 503: Spoolman is down or unreachable; other errors (switched off) say why themselves.
        if (err instanceof HttpErrorResponse && err.status === 503) {
          this.error.set(LOAD_FAILED);
        } else {
          this.error.set(errorMessage(err, LOAD_FAILED));
        }
        this.loading.set(false);
      },
    });
  }
}
