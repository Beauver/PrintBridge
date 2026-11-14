import { Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { FileSaverService } from '../../../core/files/file-saver.service';
import { PrintRequest, RequestFile } from '../../../core/models/print-request.model';
import { NO_SLICING, REQUEST, STL_INFO } from '../../../core/testing';
import { SliceStatus } from '../../../core/models/model-info.model';
import { PrintRequestsService } from '../print-requests.service';
import { RequestDetailPage, SLICING_POLL_MS } from './request-detail-page';

describe('RequestDetailPage', () => {
  const requestsService = {
    get: vi.fn(),
    downloadFile: vi.fn(),
    delete: vi.fn(),
    reslice: vi.fn(),
    uploadConfig: vi.fn(),
    addFile: vi.fn(),
    removeFile: vi.fn(),
    changeQuantity: vi.fn(),
    downloadAll: vi.fn(),
  };
  const fileSaver = { save: vi.fn() };
  const isAdmin = signal(false);
  let router: Router;
  let fixture: ComponentFixture<RequestDetailPage>;

  function httpError(status: number, message?: string) {
    let error: { message: string } | null = null;
    if (message) {
      error = { message };
    }
    return throwError(() => {
      return new HttpErrorResponse({ status, error });
    });
  }

  function create(request: PrintRequest | null = REQUEST): RequestDetailPage {
    if (request) {
      requestsService.get.mockReturnValue(of(request));
    } else {
      requestsService.get.mockReturnValue(httpError(404));
    }
    fixture = TestBed.createComponent(RequestDetailPage);
    fixture.componentRef.setInput('id', 4);
    const page = fixture.componentInstance;
    page.ngOnInit();
    return page;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    isAdmin.set(false);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: PrintRequestsService, useValue: requestsService },
        { provide: FileSaverService, useValue: fileSaver },
        { provide: AuthService, useValue: { isAdmin } },
      ],
    });
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    requestsService.uploadConfig.mockReturnValue(
      of({ maxSizeBytes: 1024, maxFiles: 10, allowedExtensions: ['stl'] }),
    );
  });

  describe('loading', () => {
    it('loads the request by route id', () => {
      const page = create();
      expect(requestsService.get).toHaveBeenCalledWith(4);
      expect(page['request']()).toEqual(REQUEST);
      expect(page['loading']()).toBe(false);
      expect(page['fileTypeLabel'](REQUEST.files[0])).toBe('STL');
    });

    it('marks unknown or foreign requests as not found', () => {
      const page = create(null);
      expect(page['notFound']()).toBe(true);
      expect(page['error']()).toBeNull();
      expect(page['loading']()).toBe(false);
    });

    it('shows other errors', () => {
      requestsService.get.mockReturnValue(httpError(500));
      fixture = TestBed.createComponent(RequestDetailPage);
      fixture.componentRef.setInput('id', 4);
      fixture.componentInstance.ngOnInit();
      expect(fixture.componentInstance['error']()).toBe('Could not load this request.');
      expect(fixture.componentInstance['notFound']()).toBe(false);
    });
  });

  describe('delete permission', () => {
    it('lets the owner delete only while submitted', () => {
      expect(create()['canDelete']()).toBe(true);
      expect(create({ ...REQUEST, status: 'IN_REVIEW' })['canDelete']()).toBe(false);
    });

    it('lets the admin delete in any status', () => {
      isAdmin.set(true);
      expect(create({ ...REQUEST, status: 'COMPLETED' })['canDelete']()).toBe(true);
    });

    it('cannot delete before the request is loaded', () => {
      expect(create(null)['canDelete']()).toBe(false);
    });
  });

  describe('review', () => {
    it('is offered to the admin on non-final requests and for paying completed ones', () => {
      isAdmin.set(true);
      expect(create()['canReview']()).toBe(true);
      const completed: PrintRequest = { ...REQUEST, status: 'COMPLETED', nextStatuses: [] };
      expect(create(completed)['canReview']()).toBe(true);
      const declined: PrintRequest = { ...REQUEST, status: 'DECLINED', nextStatuses: [] };
      expect(create(declined)['canReview']()).toBe(false);
    });

    it('is never offered to users', () => {
      expect(create()['canReview']()).toBe(false);
      expect(create(null)['canReview']()).toBe(false);
    });

    it('shows the updated request after a review change', () => {
      const page = create();
      const updated = { ...REQUEST, status: 'IN_REVIEW' as const };
      page['onReviewed'](updated);
      expect(page['request']()).toEqual(updated);
    });

    it('links back to the right list', () => {
      const page = create();
      expect(page['back']()).toEqual({ link: '/', label: 'My requests' });
      isAdmin.set(true);
      expect(page['back']()).toEqual({ link: '/admin/requests', label: 'All requests' });
    });
  });

  describe('server slicing', () => {
    function withSlicing(status: SliceStatus | null): PrintRequest {
      const modelInfo = { ...STL_INFO, slicing: { ...NO_SLICING, enabled: true, status } };
      return { ...REQUEST, files: [{ ...REQUEST.files[0], modelInfo }] };
    }

    afterEach(() => {
      vi.useRealTimers();
    });

    it('re-slices and shows the queued request', () => {
      const page = create();
      const queued = withSlicing('QUEUED');
      requestsService.reslice.mockReturnValue(of(queued));

      page['onReslice'](REQUEST.files[0]);

      expect(requestsService.reslice).toHaveBeenCalledWith(4, 7);
      expect(page['request']()).toEqual(queued);
      expect(page['reslicingId']()).toBeNull();
    });

    it('shows a re-slice error', () => {
      const page = create();
      requestsService.reslice.mockReturnValue(httpError(409, 'This file is already being sliced'));

      page['onReslice'](REQUEST.files[0]);

      expect(page['error']()).toBe('This file is already being sliced');
      expect(page['reslicingId']()).toBeNull();
    });

    /** Lets Angular run its first change detection now, so only the polling counts below. */
    function settle(): void {
      fixture.detectChanges();
      requestsService.get.mockClear();
    }

    it('reloads while slicing until it is done', () => {
      vi.useFakeTimers();
      create(withSlicing('QUEUED'));
      settle();
      requestsService.get.mockReturnValue(of(withSlicing('RUNNING')));

      vi.advanceTimersByTime(SLICING_POLL_MS);
      expect(requestsService.get).toHaveBeenCalledTimes(1);

      requestsService.get.mockReturnValue(of(withSlicing('DONE')));
      vi.advanceTimersByTime(SLICING_POLL_MS);
      expect(requestsService.get).toHaveBeenCalledTimes(2);

      vi.advanceTimersByTime(SLICING_POLL_MS * 3);
      expect(requestsService.get).toHaveBeenCalledTimes(2);
    });

    it('keeps trying after a failed reload', () => {
      vi.useFakeTimers();
      const page = create(withSlicing('RUNNING'));
      settle();
      requestsService.get.mockReturnValue(httpError(500));

      vi.advanceTimersByTime(SLICING_POLL_MS * 2);

      expect(requestsService.get).toHaveBeenCalledTimes(2);
      expect(page['request']()).toEqual(withSlicing('RUNNING'));
    });

    it('stops reloading when the page closes', () => {
      vi.useFakeTimers();
      create(withSlicing('QUEUED'));

      fixture.destroy();
      vi.advanceTimersByTime(SLICING_POLL_MS * 2);

      expect(requestsService.get).toHaveBeenCalledTimes(1);
    });
  });

  describe('download', () => {
    it('saves the downloaded file under its original name', () => {
      const blob = new Blob(['x']);
      requestsService.downloadFile.mockReturnValue(of(blob));
      const page = create();
      page['download'](REQUEST.files[0]);
      expect(requestsService.downloadFile).toHaveBeenCalledWith(4, 7);
      expect(fileSaver.save).toHaveBeenCalledWith(blob, 'benchy.stl');
      expect(page['downloadingId']()).toBeNull();
    });

    it('shows a download error', () => {
      requestsService.downloadFile.mockReturnValue(httpError(404, 'File not found'));
      const page = create();
      page['download'](REQUEST.files[0]);
      expect(page['error']()).toBe('File not found');
      expect(page['downloadingId']()).toBeNull();
      expect(fileSaver.save).not.toHaveBeenCalled();
    });
  });

  describe('delete', () => {
    it('asks for confirmation and can cancel', () => {
      const page = create();
      page['askDelete']();
      expect(page['confirmingDelete']()).toBe(true);
      page['cancelDelete']();
      expect(page['confirmingDelete']()).toBe(false);
      expect(requestsService.delete).not.toHaveBeenCalled();
    });

    it('deletes and goes back to the list', () => {
      requestsService.delete.mockReturnValue(of(undefined));
      const page = create();
      page['askDelete']();
      page['confirmDelete']();
      expect(requestsService.delete).toHaveBeenCalledWith(4);
      expect(router.navigate).toHaveBeenCalledWith(['/']);
      expect(page['deleting']()).toBe(false);
    });

    it('shows a delete error', () => {
      requestsService.delete.mockReturnValue(
        httpError(409, 'Only submitted requests can be deleted'),
      );
      const page = create();
      page['askDelete']();
      page['confirmDelete']();
      expect(page['error']()).toBe('Only submitted requests can be deleted');
      expect(page['confirmingDelete']()).toBe(false);
      expect(router.navigate).not.toHaveBeenCalled();
    });
  });

  describe('files (#23)', () => {
    const LID: RequestFile = {
      ...REQUEST.files[0],
      id: 8,
      originalFilename: 'lid.stl',
      quantity: 3,
    };
    const TWO_FILES: PrintRequest = { ...REQUEST, files: [REQUEST.files[0], LID] };

    function quantityInput(value: string): HTMLInputElement {
      const input = document.createElement('input');
      input.value = value;
      return input;
    }

    function changeQuantity(page: RequestDetailPage, file: RequestFile, input: HTMLInputElement) {
      page['changeQuantity'](file, { target: input } as unknown as Event);
    }

    it('lets the owner change files and quantities only while submitted', () => {
      const page = create(TWO_FILES);
      expect(page['canChangeFiles']()).toBe(true);
      expect(page['canChangeQuantity']()).toBe(true);
      expect(page['canRemoveFiles']()).toBe(true);

      const inReview = create({ ...TWO_FILES, status: 'IN_REVIEW', nextStatuses: ['ACCEPTED'] });
      expect(inReview['canChangeFiles']()).toBe(false);
      expect(inReview['canChangeQuantity']()).toBe(false);
      expect(inReview['canRemoveFiles']()).toBe(false);
    });

    it('lets the admin change files always and quantities until final', () => {
      isAdmin.set(true);
      const printing = create({ ...TWO_FILES, status: 'PRINTING', nextStatuses: ['COMPLETED'] });
      expect(printing['canChangeFiles']()).toBe(true);
      expect(printing['canChangeQuantity']()).toBe(true);

      const completed = create({ ...TWO_FILES, status: 'COMPLETED', nextStatuses: [] });
      expect(completed['canChangeFiles']()).toBe(true);
      expect(completed['canRemoveFiles']()).toBe(true);
      expect(completed['canChangeQuantity']()).toBe(false);
    });

    it('never offers to remove the last file', () => {
      isAdmin.set(true);
      expect(create()['canRemoveFiles']()).toBe(false);
    });

    it('offers nothing before the request is loaded', () => {
      const page = create(null);
      expect(page['canChangeFiles']()).toBe(false);
      expect(page['canChangeQuantity']()).toBe(false);
      expect(page['canRemoveFiles']()).toBe(false);
      expect(page['summary']()).toBeNull();
    });

    describe('totals and issues', () => {
      const TOTALS: PrintRequest['totals'] = {
        files: 2,
        copies: 4,
        grams: 65.25,
        weight: 'EXACT',
        printSeconds: 66000,
        printTimePartial: false,
        fits: true,
      };

      it('counts files and copies next to the Files heading', () => {
        expect(create()['fileCounts']()).toBe('1 file · 1 copy');
        expect(create({ ...TWO_FILES, totals: TOTALS })['fileCounts']()).toBe('2 files · 4 copies');
        expect(create(null)['fileCounts']()).toBeNull();
      });

      it('sums up a single file too, with nothing known yet', () => {
        expect(create()['summary']()).toEqual({ filament: null, printTime: null });
      });

      it('sums up exact grams and time', () => {
        const request: PrintRequest = { ...TWO_FILES, totals: TOTALS };
        expect(create(request)['summary']()).toEqual({
          filament: { text: '65,25 g', note: null },
          printTime: { text: '18 h 20 min', note: null },
        });
      });

      it('marks estimates, files without details and a partial time', () => {
        const request: PrintRequest = {
          ...TWO_FILES,
          files: [
            { ...REQUEST.files[0], modelInfo: STL_INFO },
            { ...LID, modelInfo: { ...STL_INFO, status: 'PENDING', totalGrams: null } },
          ],
          totals: {
            ...TOTALS,
            grams: 411.6,
            weight: 'ESTIMATED',
            printSeconds: 43500,
            printTimePartial: true,
          },
        };
        expect(create(request)['summary']()).toEqual({
          filament: { text: '~412 g', note: '(estimate; 1 file without details not counted)' },
          printTime: { text: '~12 h 5 min', note: '(partial time)' },
        });

        const partly = {
          ...request,
          totals: { ...request.totals, weight: 'PARTLY_ESTIMATED' as const },
        };
        expect(create(partly)['summary']()?.filament?.note).toBe(
          '(partly estimated; 1 file without details not counted)',
        );
      });
    });

    it('lists the files the new request form could not add', () => {
      const failedUploads = [{ filename: 'lid.stl', reason: 'The file is empty' }];
      vi.spyOn(TestBed.inject(Location), 'getState').mockReturnValue({ failedUploads });
      expect(create()['failedUploads']()).toEqual(failedUploads);
    });

    it('lists no failures without navigation state', () => {
      vi.spyOn(TestBed.inject(Location), 'getState').mockReturnValue(null);
      expect(create()['failedUploads']()).toEqual([]);
    });

    it('shows the request after a file was added', () => {
      const page = create();
      page['onFileAdded'](TWO_FILES);
      expect(page['request']()).toEqual(TWO_FILES);
    });

    it('downloads all files as a ZIP named after the request', () => {
      const blob = new Blob(['zip']);
      requestsService.downloadAll.mockReturnValue(of(blob));
      const page = create({ ...REQUEST, name: 'Box/lid' });
      page['downloadAll']();
      expect(requestsService.downloadAll).toHaveBeenCalledWith(4);
      expect(fileSaver.save).toHaveBeenCalledWith(blob, 'Box_lid.zip');
      expect(page['downloadingAll']()).toBe(false);
    });

    it('shows a ZIP download error', () => {
      requestsService.downloadAll.mockReturnValue(httpError(404, 'File not found'));
      const page = create();
      page['downloadAll']();
      expect(page['error']()).toBe('File not found');
      expect(page['downloadingAll']()).toBe(false);
    });

    it('does not download before the request is loaded', () => {
      create(null)['downloadAll']();
      expect(requestsService.downloadAll).not.toHaveBeenCalled();
    });

    it('saves a changed quantity', () => {
      const updated = { ...TWO_FILES, files: [REQUEST.files[0], { ...LID, quantity: 5 }] };
      requestsService.changeQuantity.mockReturnValue(of(updated));
      const page = create(TWO_FILES);

      changeQuantity(page, LID, quantityInput('5'));

      expect(requestsService.changeQuantity).toHaveBeenCalledWith(4, 8, 5);
      expect(page['request']()).toEqual(updated);
      expect(page['savingQuantityId']()).toBeNull();
    });

    it('ignores an unchanged quantity and puts an invalid one back', () => {
      const page = create(TWO_FILES);
      changeQuantity(page, LID, quantityInput('3'));
      const input = quantityInput('0');
      changeQuantity(page, LID, input);

      expect(requestsService.changeQuantity).not.toHaveBeenCalled();
      expect(input.value).toBe('3');
      expect(page['error']()).toBe('The quantity must be a whole number from 1 to 100');
    });

    it('puts the quantity back when saving fails', () => {
      requestsService.changeQuantity.mockReturnValue(
        httpError(409, 'Quantities can only be changed while the request is submitted'),
      );
      const page = create(TWO_FILES);
      const input = quantityInput('4');
      changeQuantity(page, LID, input);

      expect(input.value).toBe('3');
      expect(page['error']()).toBe('Quantities can only be changed while the request is submitted');
      expect(page['savingQuantityId']()).toBeNull();
    });

    it('asks before removing a file and can cancel', () => {
      const page = create(TWO_FILES);
      page['askRemove'](LID);
      expect(page['confirmingRemoveId']()).toBe(8);
      page['cancelRemove']();
      expect(page['confirmingRemoveId']()).toBeNull();
      expect(requestsService.removeFile).not.toHaveBeenCalled();
    });

    it('removes a file', () => {
      requestsService.removeFile.mockReturnValue(of(REQUEST));
      const page = create(TWO_FILES);
      page['askRemove'](LID);
      page['confirmRemove'](LID);

      expect(requestsService.removeFile).toHaveBeenCalledWith(4, 8);
      expect(page['request']()).toEqual(REQUEST);
      expect(page['confirmingRemoveId']()).toBeNull();
      expect(page['removingId']()).toBeNull();
    });

    it('shows a remove error', () => {
      requestsService.removeFile.mockReturnValue(
        httpError(409, 'A request needs at least one file; delete the request instead'),
      );
      const page = create(TWO_FILES);
      page['askRemove'](LID);
      page['confirmRemove'](LID);

      expect(page['error']()).toBe('A request needs at least one file; delete the request instead');
      expect(page['confirmingRemoveId']()).toBeNull();
      expect(page['request']()).toEqual(TWO_FILES);
    });
  });
});
