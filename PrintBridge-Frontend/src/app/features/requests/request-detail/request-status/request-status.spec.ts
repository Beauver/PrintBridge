import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { PrintRequest } from '../../../../core/models/print-request.model';
import { REQUEST } from '../../../../../testing/fixtures';
import { PrintRequestsService } from '../../print-requests.service';
import { RequestStatusControl } from './request-status';

describe('RequestStatusControl', () => {
  let fixture: ComponentFixture<RequestStatusControl>;
  let control: RequestStatusControl;
  let emitted: PrintRequest[];
  const requestsService = { changeStatus: vi.fn() };

  const IN_REVIEW: PrintRequest = {
    ...REQUEST,
    status: 'IN_REVIEW',
    nextStatuses: ['ACCEPTED', 'DECLINED'],
  };
  const ACCEPTED: PrintRequest = {
    ...REQUEST,
    status: 'ACCEPTED',
    nextStatuses: ['PRINTING', 'COMPLETED'],
  };

  function show(request: PrintRequest, isAdmin = true): void {
    fixture.componentRef.setInput('request', request);
    fixture.componentRef.setInput('isAdmin', isAdmin);
    fixture.detectChanges();
  }

  function typeReason(value: string): void {
    control['setReason']({ target: { value } } as unknown as Event);
  }

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [{ provide: PrintRequestsService, useValue: requestsService }],
    });
    fixture = TestBed.createComponent(RequestStatusControl);
    control = fixture.componentInstance;
    emitted = [];
    control.changed.subscribe((request) => {
      emitted.push(request);
    });
    show(REQUEST);
  });

  it('is a dropdown for the admin while the request can still move', () => {
    expect(control['editable']()).toBe(true);
    show(REQUEST, false);
    expect(control['editable']()).toBe(false);
    show({ ...REQUEST, status: 'COMPLETED', nextStatuses: [] });
    expect(control['editable']()).toBe(false);
  });

  it('applies a non-final status right away', () => {
    requestsService.changeStatus.mockReturnValue(of(IN_REVIEW));
    control['choose']('IN_REVIEW');

    expect(requestsService.changeStatus).toHaveBeenCalledWith(4, 'IN_REVIEW', null);
    expect(emitted).toEqual([IN_REVIEW]);
    expect(control['saving']()).toBe(false);
  });

  it('asks for confirmation (with reason) before declining', () => {
    control['choose']('DECLINED');
    expect(control['pendingStatus']()).toBe('DECLINED');
    expect(requestsService.changeStatus).not.toHaveBeenCalled();

    requestsService.changeStatus.mockReturnValue(of({ ...REQUEST, status: 'DECLINED' }));
    typeReason('Too big');
    control['confirm']();

    expect(requestsService.changeStatus).toHaveBeenCalledWith(4, 'DECLINED', 'Too big');
    expect(control['pendingStatus']()).toBeNull();
    expect(emitted).toHaveLength(1);
  });

  it('can cancel a pending final status, and starts with an empty reason', () => {
    control['choose']('DECLINED');
    typeReason('half');
    control['cancel']();
    expect(control['pendingStatus']()).toBeNull();
    control['choose']('DECLINED');
    expect(control['reason']()).toBe('');
    expect(requestsService.changeStatus).not.toHaveBeenCalled();
  });

  it('does not confirm with a too long reason or nothing pending', () => {
    control['confirm']();
    control['choose']('DECLINED');
    typeReason('x'.repeat(2001));
    control['confirm']();
    expect(requestsService.changeStatus).not.toHaveBeenCalled();
  });

  it('confirms completing without a reason', () => {
    show({ ...ACCEPTED, finalPrice: 10 });
    requestsService.changeStatus.mockReturnValue(of({ ...ACCEPTED, status: 'COMPLETED' }));
    control['choose']('COMPLETED');
    typeReason('ignored');
    control['confirm']();
    expect(requestsService.changeStatus).toHaveBeenCalledWith(4, 'COMPLETED', null);
  });

  it('disables completing until a price is set', () => {
    show(ACCEPTED);
    expect(control['isDisabled']('COMPLETED')).toBe(true);
    expect(control['isDisabled']('PRINTING')).toBe(false);
    control['choose']('COMPLETED');
    expect(control['pendingStatus']()).toBeNull();
    show({ ...ACCEPTED, finalPrice: 0 });
    expect(control['isDisabled']('COMPLETED')).toBe(false);
  });

  it('shows the error when the change fails', () => {
    requestsService.changeStatus.mockReturnValue(
      throwError(() => {
        return new HttpErrorResponse({
          status: 409,
          error: { message: 'Cannot change status from SUBMITTED to IN_REVIEW' },
        });
      }),
    );
    control['choose']('IN_REVIEW');
    expect(control['error']()).toBe('Cannot change status from SUBMITTED to IN_REVIEW');
    expect(control['saving']()).toBe(false);
    expect(emitted).toEqual([]);
    control['dismissError']();
    expect(control['error']()).toBeNull();
  });

  it('labels and colours the status', () => {
    expect(control['label']('PRINTING')).toBe('Printing');
    expect(control['badgeClass']('PRINTING')).toBe('text-bg-warning');
  });
});
