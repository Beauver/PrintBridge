import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/** Whether first-run setup (creating the admin) still has to happen. Loaded once at startup. */
@Injectable({ providedIn: 'root' })
export class SetupStatusService {
  private readonly http = inject(HttpClient);
  private readonly setupRequired = signal(false);

  readonly required = this.setupRequired.asReadonly();

  async load(): Promise<void> {
    try {
      const status = await firstValueFrom(this.http.get<{ setupRequired: boolean }>('/api/setup'));
      this.setupRequired.set(status.setupRequired);
    } catch {
      // Backend unreachable: continue to the login page, which shows the connection error.
      this.setupRequired.set(false);
    }
  }

  markDone(): void {
    this.setupRequired.set(false);
  }
}
