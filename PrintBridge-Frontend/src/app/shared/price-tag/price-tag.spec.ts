import { TestBed } from '@angular/core/testing';
import { PriceTag } from './price-tag';

describe('PriceTag', () => {
  function create(price: number | null, paidAt: string | null = null): PriceTag {
    const fixture = TestBed.createComponent(PriceTag);
    fixture.componentRef.setInput('price', price);
    fixture.componentRef.setInput('paidAt', paidAt);
    fixture.componentRef.setInput('currency', 'USD');
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('has no amount while the price is pending', () => {
    const tag = create(null);
    expect(tag['formatted']()).toBeNull();
    expect(tag['paid']()).toBe(false);
  });

  it('formats the price in its currency, in the browser locale (en-US in tests)', () => {
    expect(create(6900.42)['formatted']()).toBe('$6,900.42');
  });

  it('knows when the request is paid', () => {
    expect(create(10, '2026-03-01T00:00:00Z')['paid']()).toBe(true);
  });

  it('shows the estimate while there is no final price', () => {
    const fixture = TestBed.createComponent(PriceTag);
    fixture.componentRef.setInput('price', null);
    fixture.componentRef.setInput('currency', 'EUR');
    fixture.componentRef.setInput('estimate', { amount: 12.34, partial: false });
    const tag = fixture.componentInstance;
    expect(tag['estimated']()?.text).toBe('~ €12.34');
    expect(tag['estimated']()?.note).toBe('(estimate)');

    fixture.componentRef.setInput('estimate', { amount: 12.34, partial: true });
    expect(tag['estimated']()?.note).toBe('(partial estimate)');

    fixture.componentRef.setInput('price', 20);
    expect(tag['estimated']()).toBeNull();
    fixture.componentRef.setInput('price', null);
    fixture.componentRef.setInput('estimate', null);
    expect(tag['estimated']()).toBeNull();
  });
});
