import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ALICE, REQUEST_SUMMARY } from '../../../core/testing';
import { PrintRequestsService } from '../print-requests.service';
import { MyRequestsPage } from './my-requests-page';

describe('MyRequestsPage', () => {
  const requestsService = { listOwn: vi.fn() };

  function create(): MyRequestsPage {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: PrintRequestsService, useValue: requestsService },
        { provide: AuthService, useValue: { user: signal(ALICE), isAdmin: signal(false) } },
      ],
    });
    const page = TestBed.createComponent(MyRequestsPage).componentInstance;
    page.ngOnInit();
    return page;
  }

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('loads the own requests', () => {
    requestsService.listOwn.mockReturnValue(of([REQUEST_SUMMARY]));
    const page = create();
    expect(page['requests']()).toEqual([REQUEST_SUMMARY]);
    expect(page['loading']()).toBe(false);
    expect(page['error']()).toBeNull();
  });

  it('shows a load error and can retry', () => {
    requestsService.listOwn.mockReturnValue(
      throwError(() => {
        return new HttpErrorResponse({ status: 500 });
      }),
    );
    const page = create();
    expect(page['error']()).toBe('Could not load your requests.');
    expect(page['loading']()).toBe(false);

    requestsService.listOwn.mockReturnValue(of([]));
    page['load']();
    expect(page['error']()).toBeNull();
    expect(page['requests']()).toEqual([]);
  });
});
