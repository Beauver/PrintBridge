import { Component, inject, OnInit, signal } from '@angular/core';
import { LicenseService } from '../../core/license/license.service';

/** The licence text on gnu.org. */
export const LICENCE_URL = 'https://www.gnu.org/licenses/agpl-3.0.html';
/** Written by the Angular production build (`extractLicenses`), next to index.html. */
export const THIRD_PARTY_LICENCES_URL = '/3rdpartylicenses.txt';

/**
 * Licence and source on every page (#16). The AGPL-3.0 asks that people using PrintBridge over
 * a network can get its source; the link comes from the backend's `SOURCE_URL`.
 */
@Component({
  selector: 'app-footer',
  templateUrl: './footer.html',
  styleUrl: './footer.css',
})
export class Footer implements OnInit {
  private readonly licenseService = inject(LicenseService);

  /** Null until loaded; the Source link waits for it. */
  protected readonly sourceUrl = signal<string | null>(null);
  protected readonly licenceUrl = LICENCE_URL;
  protected readonly thirdPartyLicencesUrl = THIRD_PARTY_LICENCES_URL;

  ngOnInit(): void {
    this.licenseService.get().subscribe({
      next: (license) => {
        this.sourceUrl.set(license.sourceUrl);
      },
      error: () => {
        // The backend is unreachable; the rest of the page says so already.
      },
    });
  }
}
