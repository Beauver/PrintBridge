import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { PrintRequest } from '../../../../core/models/print-request.model';
import { REQUEST } from '../../../../../testing/fixtures';
import { PrintRequestsService } from '../../print-requests.service';
import { ReviewPanel } from './review-panel';

describe('ReviewPanel', () => {
  let fixture: ComponentFixture<ReviewPanel>;
  let panel: ReviewPanel;
  let emitted: PrintRequest[];
  const requestsService = { changePrice: vi.fn(), changePaid: vi.fn() };

  const ACCEPTED: PrintRequest = {
    ...REQUEST,
    status: 'ACCEPTED',
    nextStatuses: ['PRINTING', 'COMPLETED'],
  };

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

    it('hints and saves the estimate when left empty', () => {
      expect(panel['pricePlaceholder']()).toBe('69,42');
      const estimated = { ...REQUEST, estimatedPrice: { amount: 12.3, partial: false } };
      show(estimated);
      expect(panel['pricePlaceholder']()).toBe('12,30');
      requestsService.changePrice.mockReturnValue(of({ ...estimated, finalPrice: 12.3 }));

      panel['price'].setValue('  ');
      panel['savePrice']();

      expect(requestsService.changePrice).toHaveBeenCalledWith(4, 12.3);
      expect(panel['notice']()?.replace(/\s/g, ' ')).toBe('Price set to the estimate, € 12,30.');

      // A typed price still wins over the estimate.
      panel['price'].setValue('20');
      panel['savePrice']();
      expect(requestsService.changePrice).toHaveBeenLastCalledWith(4, 20);
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

    it('is only offered from accepted on', () => {
      expect(panel['canPay']()).toBe(false);
      show(PRICED);
      expect(panel['canPay']()).toBe(true);
      show({ ...PRICED, status: 'PRINTING', nextStatuses: ['COMPLETED', 'ACCEPTED', 'DECLINED'] });
      expect(panel['canPay']()).toBe(true);
      expect(panel['editable']()).toBe(true);
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
