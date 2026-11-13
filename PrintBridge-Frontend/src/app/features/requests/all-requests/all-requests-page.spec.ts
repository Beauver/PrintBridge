import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { PrintRequestSummary } from '../../../core/models/print-request.model';
import { REQUEST_SUMMARY } from '../../../core/testing';
import { PrintRequestsService } from '../print-requests.service';
import { AllRequestsPage } from './all-requests-page';

describe('AllRequestsPage', () => {
  let page: AllRequestsPage;
  const requestsService = { listAll: vi.fn() };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
    requestsService.listAll.mockReturnValue(of([REQUEST_SUMMARY]));
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: PrintRequestsService, useValue: requestsService }],
    });
    // Let Angular run ngOnInit: calling it by hand would run it twice once timers advance.
    const fixture = TestBed.createComponent(AllRequestsPage);
    fixture.detectChanges();
    page = fixture.componentInstance;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('loads open requests on start', () => {
    expect(requestsService.listAll).toHaveBeenCalledWith('OPEN', '');
    expect(page['requests']()).toEqual([REQUEST_SUMMARY]);
    expect(page['loading']()).toBe(false);
  });

  it('offers open, all and every single status', () => {
    const values = page['filterOptions'].map((option) => {
      return option.value;
    });
    expect(values).toEqual([
      'OPEN',
      'ALL',
      'SUBMITTED',
      'IN_REVIEW',
      'ACCEPTED',
      'PRINTING',
      'DECLINED',
      'COMPLETED',
    ]);
    expect(page['filterOptions'][3].label).toBe('In review');
  });

  it('reloads right away when the status filter changes', () => {
    page['statusFilter'].setValue('COMPLETED');
    expect(requestsService.listAll).toHaveBeenLastCalledWith('COMPLETED', '');
  });

  it('searches after a short typing pause', () => {
    page['search'].setValue('be');
    page['search'].setValue('ben');
    expect(requestsService.listAll).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(300);
    expect(requestsService.listAll).toHaveBeenCalledTimes(2);
    expect(requestsService.listAll).toHaveBeenLastCalledWith('OPEN', 'ben');
  });

  it('ignores the answer of an outdated search', () => {
    const slow = new Subject<PrintRequestSummary[]>();
    requestsService.listAll.mockReturnValueOnce(slow).mockReturnValueOnce(of([]));
    page['load']();
    page['load']();
    slow.next([REQUEST_SUMMARY]);
    expect(page['requests']()).toEqual([]);
  });

  it('shows a load error and keeps working afterwards', () => {
    requestsService.listAll.mockReturnValue(
      throwError(() => {
        return new HttpErrorResponse({ status: 500 });
      }),
    );
    page['load']();
    expect(page['error']()).toBe('Could not load the requests.');
    expect(page['requests']()).toEqual([]);
    expect(page['loading']()).toBe(false);

    requestsService.listAll.mockReturnValue(of([REQUEST_SUMMARY]));
    page['load']();
    expect(page['error']()).toBeNull();
    expect(page['requests']()).toEqual([REQUEST_SUMMARY]);
  });
});
