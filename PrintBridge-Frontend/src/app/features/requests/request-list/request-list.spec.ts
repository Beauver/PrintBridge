import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { REQUEST_SUMMARY } from '../../../../testing/fixtures';
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

  it('shows the item progress from Accepted on with more than one item (#61)', () => {
    const list = TestBed.createComponent(RequestList).componentInstance;
    const accepted = {
      ...REQUEST_SUMMARY,
      status: 'ACCEPTED' as const,
      itemsCompleted: 2,
      itemsActive: 3,
    };
    expect(list['progress'](accepted)).toBe('2/3 completed');
    expect(list['progress']({ ...accepted, status: 'IN_REVIEW' })).toBeNull();
    expect(list['progress']({ ...accepted, itemsActive: 1 })).toBeNull();
  });
});
