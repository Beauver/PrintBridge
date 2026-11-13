import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AboutService } from '../../core/about/about.service';
import { Footer } from './footer';

describe('Footer', () => {
  const aboutService = { get: vi.fn() };

  function create(): Footer {
    TestBed.configureTestingModule({
      providers: [{ provide: AboutService, useValue: aboutService }],
    });
    const footer = TestBed.createComponent(Footer).componentInstance;
    footer.ngOnInit();
    return footer;
  }

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('links to the source of this installation', () => {
    aboutService.get.mockReturnValue(
      of({ sourceUrl: 'https://example.com/fork', licence: 'AGPL-3.0-or-later' }),
    );

    const footer = create();

    expect(footer['sourceUrl']()).toBe('https://example.com/fork');
    expect(footer['licenceUrl']).toBe('https://www.gnu.org/licenses/agpl-3.0.html');
    expect(footer['thirdPartyLicencesUrl']).toBe('/3rdpartylicenses.txt');
  });

  it('leaves the source link out while the backend is unreachable', () => {
    aboutService.get.mockReturnValue(
      throwError(() => {
        return new Error('down');
      }),
    );

    expect(create()['sourceUrl']()).toBeNull();
  });
});
