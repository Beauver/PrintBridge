import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { FileSaverService } from '../../../core/files/file-saver.service';
import { PrintRequest } from '../../../core/models/print-request.model';
import { REQUEST } from '../../../core/testing';
import { PrintRequestsService } from '../print-requests.service';
import { RequestDetailPage } from './request-detail-page';

describe('RequestDetailPage', () => {
  const requestsService = { get: vi.fn(), downloadFile: vi.fn(), delete: vi.fn() };
  const fileSaver = { save: vi.fn() };
  const isAdmin = signal(false);
  let router: Router;

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
    const fixture = TestBed.createComponent(RequestDetailPage);
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
      const fixture = TestBed.createComponent(RequestDetailPage);
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
});
