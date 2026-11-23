import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { PrintRequest } from '../../../../core/models/print-request.model';
import { REQUEST } from '../../../../../testing/fixtures';
import { PrintRequestsService } from '../../print-requests.service';
import { CancelRequestControl } from './cancel-request';

describe('CancelRequestControl', () => {
  const requestsService = { cancel: vi.fn() };
  let changed: PrintRequest[];

  function create(request: PrintRequest = REQUEST, isAdmin = false): CancelRequestControl {
    const fixture = TestBed.createComponent(CancelRequestControl);
    fixture.componentRef.setInput('request', request);
    fixture.componentRef.setInput('isAdmin', isAdmin);
    const control = fixture.componentInstance;
    control.changed.subscribe((updated) => {
      changed.push(updated);
    });
    return control;
  }

  function typeReason(control: CancelRequestControl, reason: string): void {
    const textarea = document.createElement('textarea');
    textarea.value = reason;
    control['setReason']({ target: textarea } as unknown as Event);
  }

  beforeEach(() => {
    vi.clearAllMocks();
    changed = [];
    TestBed.configureTestingModule({
      providers: [{ provide: PrintRequestsService, useValue: requestsService }],
    });
  });

  it('is offered to the requester before acceptance only', () => {
    expect(create()['available']()).toBe(true);
    expect(create({ ...REQUEST, status: 'IN_REVIEW' })['available']()).toBe(true);
    expect(create({ ...REQUEST, status: 'ACCEPTED' })['available']()).toBe(false);
    expect(create({ ...REQUEST, status: 'CANCELLED' })['available']()).toBe(false);
  });

  it('is never offered to the admin, who declines instead', () => {
    expect(create(REQUEST, true)['available']()).toBe(false);
  });

  it('asks for confirmation and can be kept', () => {
    const control = create();
    control['ask']();
    expect(control['confirming']()).toBe(true);
    expect(control['reason']()).toBe('');

    control['keep']();
    expect(control['confirming']()).toBe(false);
    expect(requestsService.cancel).not.toHaveBeenCalled();
  });

  it('cancels with the typed reason and hands over the cancelled request', () => {
    const cancelled: PrintRequest = { ...REQUEST, status: 'CANCELLED', cancelReason: 'Too late' };
    requestsService.cancel.mockReturnValue(of(cancelled));
    const control = create();
    control['ask']();
    typeReason(control, 'Too late');

    control['confirm']();

    expect(requestsService.cancel).toHaveBeenCalledWith(REQUEST.id, 'Too late');
    expect(changed).toEqual([cancelled]);
    expect(control['confirming']()).toBe(false);
    expect(control['saving']()).toBe(false);
  });

  it('refuses a reason that is too long', () => {
    const control = create();
    control['ask']();
    typeReason(control, 'x'.repeat(2001));

    control['confirm']();

    expect(control['error']()).toBe('Reason must be at most 2000 characters');
    expect(requestsService.cancel).not.toHaveBeenCalled();
  });

  it('shows why cancelling failed and keeps the confirmation open', () => {
    requestsService.cancel.mockReturnValue(
      throwError(() => {
        return new HttpErrorResponse({
          status: 409,
          error: { message: 'Only a submitted or in-review request can be cancelled' },
        });
      }),
    );
    const control = create();
    control['ask']();

    control['confirm']();

    expect(control['error']()).toBe('Only a submitted or in-review request can be cancelled');
    expect(control['confirming']()).toBe(true);
    expect(control['saving']()).toBe(false);

    control['dismissError']();
    expect(control['error']()).toBeNull();
  });
});
