import { TestBed } from '@angular/core/testing';
import { PriceTag } from './price-tag';

describe('PriceTag', () => {
  function create(price: number | null, paidAt: string | null = null): PriceTag {
    const fixture = TestBed.createComponent(PriceTag);
    fixture.componentRef.setInput('price', price);
    fixture.componentRef.setInput('paidAt', paidAt);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('has no amount while the price is pending', () => {
    const tag = create(null);
    expect(tag['formatted']()).toBeNull();
    expect(tag['paid']()).toBe(false);
  });

  it('formats the price in euros', () => {
    expect(create(6900.42)['formatted']()?.replace(/\s/g, ' ')).toBe('€ 6.900,42');
  });

  it('knows when the request is paid', () => {
    expect(create(10, '2026-03-01T00:00:00Z')['paid']()).toBe(true);
  });
});
