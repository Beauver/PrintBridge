import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { LicenseService } from '../../core/license/license.service';
import { Footer } from './footer';

describe('Footer', () => {
  const licenseService = { get: vi.fn() };

  function create(): Footer {
    TestBed.configureTestingModule({
      providers: [{ provide: LicenseService, useValue: licenseService }],
    });
    const footer = TestBed.createComponent(Footer).componentInstance;
    footer.ngOnInit();
    return footer;
  }

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('links to the source of this installation', () => {
    licenseService.get.mockReturnValue(
      of({ license: 'AGPL-3.0-or-later', sourceUrl: 'https://example.com/fork' }),
    );

    const footer = create();

    expect(footer['sourceUrl']()).toBe('https://example.com/fork');
    expect(footer['licenceUrl']).toBe('https://www.gnu.org/licenses/agpl-3.0.html');
    expect(footer['thirdPartyLicencesUrl']).toBe('/3rdpartylicenses.txt');
  });

  it('leaves the source link out while the backend is unreachable', () => {
    licenseService.get.mockReturnValue(
      throwError(() => {
        return new Error('down');
      }),
    );

    expect(create()['sourceUrl']()).toBeNull();
  });
});
