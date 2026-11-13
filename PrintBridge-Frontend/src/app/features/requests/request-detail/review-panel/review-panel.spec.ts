import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { PrintRequest } from '../../../../core/models/print-request.model';
import { REQUEST } from '../../../../core/testing';
import { PrintRequestsService } from '../../print-requests.service';
import { ReviewPanel } from './review-panel';

describe('ReviewPanel', () => {
  let fixture: ComponentFixture<ReviewPanel>;
  let panel: ReviewPanel;
  let emitted: PrintRequest[];
  const requestsService = { changeStatus: vi.fn(), changePrice: vi.fn(), changePaid: vi.fn() };

  const IN_REVIEW: PrintRequest = {
    ...REQUEST,
    status: 'IN_REVIEW',
    nextStatuses: ['ACCEPTED', 'DECLINED'],
  };
  const ACCEPTED: PrintRequest = { ...REQUEST, status: 'ACCEPTED', nextStatuses: ['COMPLETED'] };

  function httpError(status: number, message: string) {
    return throwError(() => {
      return new HttpErrorResponse({ status, error: { message } });
    });
  }

  function show(request: PrintRequest): void {
    fixture.componentRef.setInput('request', request);
    fixture.detectChanges();
  }

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [{ provide: PrintRequestsService, useValue: requestsService }],
    });
    fixture = TestBed.createComponent(ReviewPanel);
    panel = fixture.componentInstance;
    emitted = [];
    panel.changed.subscribe((request) => {
      emitted.push(request);
    });
    show(REQUEST);
  });

  describe('status', () => {
    it('offers the current status followed by the allowed next ones', () => {
      expect(panel['status'].value).toBe('SUBMITTED');
      expect(panel['statusOptions']()).toEqual(['SUBMITTED', 'IN_REVIEW', 'ACCEPTED', 'DECLINED']);
    });

    it('applies a non-final status right away', () => {
      requestsService.changeStatus.mockReturnValue(of(IN_REVIEW));
      panel['status'].setValue('IN_REVIEW');

      expect(requestsService.changeStatus).toHaveBeenCalledWith(4, 'IN_REVIEW', null);
      expect(emitted).toEqual([IN_REVIEW]);
      expect(panel['notice']()).toBe('Status changed to In review.');
      expect(panel['status'].enabled).toBe(true);
    });

    it('does nothing when the current status is picked again', () => {
      panel['status'].setValue('SUBMITTED');
      expect(requestsService.changeStatus).not.toHaveBeenCalled();
      expect(panel['pendingStatus']()).toBeNull();
    });

    it('asks for confirmation (with reason) before declining', () => {
      panel['status'].setValue('DECLINED');
      expect(panel['pendingStatus']()).toBe('DECLINED');
      expect(requestsService.changeStatus).not.toHaveBeenCalled();

      requestsService.changeStatus.mockReturnValue(of({ ...REQUEST, status: 'DECLINED' }));
      panel['declineReason'].setValue('Too big');
      panel['confirmPending']();

      expect(requestsService.changeStatus).toHaveBeenCalledWith(4, 'DECLINED', 'Too big');
      expect(panel['pendingStatus']()).toBeNull();
      expect(emitted).toHaveLength(1);
    });

    it('can cancel a pending final status', () => {
      panel['status'].setValue('DECLINED');
      panel['cancelPending']();
      expect(panel['pendingStatus']()).toBeNull();
      expect(panel['status'].value).toBe('SUBMITTED');
      expect(requestsService.changeStatus).not.toHaveBeenCalled();
    });

    it('does not confirm with a too long reason or nothing pending', () => {
      panel['confirmPending']();
      panel['status'].setValue('DECLINED');
      panel['declineReason'].setValue('x'.repeat(2001));
      panel['confirmPending']();
      expect(requestsService.changeStatus).not.toHaveBeenCalled();
    });

    it('confirms completing without a reason', () => {
      show({ ...ACCEPTED, finalPrice: 10 });
      requestsService.changeStatus.mockReturnValue(of({ ...ACCEPTED, status: 'COMPLETED' }));
      panel['status'].setValue('COMPLETED');
      panel['declineReason'].setValue('ignored');
      panel['confirmPending']();
      expect(requestsService.changeStatus).toHaveBeenCalledWith(4, 'COMPLETED', null);
    });

    it('disables completing until a price is set', () => {
      show(ACCEPTED);
      expect(panel['needsPrice']()).toBe(true);
      expect(panel['isOptionDisabled']('COMPLETED')).toBe(true);
      expect(panel['isOptionDisabled']('ACCEPTED')).toBe(false);
      show({ ...ACCEPTED, finalPrice: 0 });
      expect(panel['isOptionDisabled']('COMPLETED')).toBe(false);
    });

    it('shows the error and resets the dropdown when the change fails', () => {
      requestsService.changeStatus.mockReturnValue(
        httpError(409, 'Cannot change status from SUBMITTED to IN_REVIEW'),
      );
      panel['status'].setValue('IN_REVIEW');
      expect(panel['error']()).toBe('Cannot change status from SUBMITTED to IN_REVIEW');
      expect(panel['status'].value).toBe('SUBMITTED');
      expect(panel['status'].enabled).toBe(true);
      expect(emitted).toEqual([]);
    });
  });

  describe('price', () => {
    it('starts with the current price in Dutch format', () => {
      show({ ...REQUEST, finalPrice: 6900.5 });
      expect(panel['price'].value).toBe('6.900,50');
      show(REQUEST);
      expect(panel['price'].value).toBe('');
    });

    it('keeps typed input when the same request is reloaded', () => {
      panel['price'].setValue('12,50');
      panel['price'].markAsTouched();

      show({ ...REQUEST, files: [] });
      expect(panel['price'].value).toBe('12,50');
      expect(panel['price'].touched).toBe(true);

      show({ ...REQUEST, updatedAt: '2030-01-01T00:00:00Z' });
      expect(panel['price'].value).toBe('');
    });

    it('saves a price typed in either style', () => {
      const priced = { ...REQUEST, finalPrice: 6900.42 };
      requestsService.changePrice.mockReturnValue(of(priced));
      panel['price'].setValue('6.900,42');
      panel['savePrice']();
      expect(requestsService.changePrice).toHaveBeenCalledWith(4, 6900.42);
      expect(emitted).toEqual([priced]);
      expect(panel['notice']()?.replace(/\s/g, ' ')).toBe('Price set to € 6.900,42.');

      panel['price'].setValue('69.42');
      panel['savePrice']();
      expect(requestsService.changePrice).toHaveBeenLastCalledWith(4, 69.42);
    });

    it('clears the price', () => {
      requestsService.changePrice.mockReturnValue(of(REQUEST));
      panel['clearPrice']();
      expect(requestsService.changePrice).toHaveBeenCalledWith(4, null);
      expect(panel['notice']()).toBe('Price cleared.');
    });

    it('validates before saving', () => {
      const cases: [string, string][] = [
        ['', 'Enter a price'],
        ['69,4231', 'Enter a price like 69,42 or 6.900,42'],
        ['-1', 'Enter a price like 69,42 or 6.900,42'],
        ['100.000.000', 'Price must be at most € 99.999.999,99'],
      ];
      for (const [value, message] of cases) {
        panel['price'].setValue(value);
        panel['savePrice']();
        expect(panel['priceError']()?.replace(/\s/g, ' ')).toBe(message);
      }
      expect(requestsService.changePrice).not.toHaveBeenCalled();
    });

    it('accepts zero', () => {
      panel['price'].setValue('0');
      expect(panel['price'].valid).toBe(true);
    });

    it('shows no error before the input is touched', () => {
      panel['price'].setValue('abc');
      expect(panel['priceError']()).toBeNull();
    });

    it('shows a save error', () => {
      requestsService.changePrice.mockReturnValue(
        httpError(409, 'This request is DECLINED and can no longer be changed'),
      );
      panel['price'].setValue('5');
      panel['savePrice']();
      expect(panel['error']()).toBe('This request is DECLINED and can no longer be changed');
      expect(panel['saving']()).toBe(false);
    });
  });

  describe('paid', () => {
    const PRICED: PrintRequest = { ...ACCEPTED, finalPrice: 20 };
    const PAID: PrintRequest = { ...PRICED, paidAt: '2026-03-01T00:00:00Z' };

    it('is only offered on accepted and completed requests', () => {
      expect(panel['canPay']()).toBe(false);
      show(PRICED);
      expect(panel['canPay']()).toBe(true);
      show({ ...PRICED, status: 'COMPLETED', nextStatuses: [] });
      expect(panel['canPay']()).toBe(true);
      expect(panel['editable']()).toBe(false);
    });

    it('marks a request paid and locks the price', () => {
      show(PRICED);
      requestsService.changePaid.mockReturnValue(of(PAID));
      panel['togglePaid']();
      expect(requestsService.changePaid).toHaveBeenCalledWith(4, true);
      expect(panel['notice']()).toBe('Marked as paid.');
      expect(emitted).toEqual([PAID]);

      show(PAID);
      expect(panel['paid']()).toBe(true);
      expect(panel['price'].disabled).toBe(true);
    });

    it('marks a paid request unpaid and unlocks the price', () => {
      show(PAID);
      requestsService.changePaid.mockReturnValue(of(PRICED));
      panel['togglePaid']();
      expect(requestsService.changePaid).toHaveBeenCalledWith(4, false);
      expect(panel['notice']()).toBe('Marked as unpaid.');

      show(PRICED);
      expect(panel['price'].enabled).toBe(true);
    });

    it('shows a paid error', () => {
      show(PRICED);
      requestsService.changePaid.mockReturnValue(
        httpError(409, 'Set a final price before marking the request as paid'),
      );
      panel['togglePaid']();
      expect(panel['error']()).toBe('Set a final price before marking the request as paid');
    });
  });
});
