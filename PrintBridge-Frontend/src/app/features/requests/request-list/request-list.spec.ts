import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { REQUEST_SUMMARY } from '../../../core/testing';
import { RequestList } from './request-list';

describe('RequestList', () => {
  it('takes requests and hides the owner by default', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(RequestList);
    fixture.componentRef.setInput('requests', [REQUEST_SUMMARY]);
    fixture.detectChanges();

    const list = fixture.componentInstance;
    expect(list.requests()).toEqual([REQUEST_SUMMARY]);
    expect(list.showOwner()).toBe(false);
    expect(list['statusLabel']('SUBMITTED')).toBe('Submitted');
  });
});
