import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Subject, throwError } from 'rxjs';
import { PrintRequest, RequestFile } from '../../../../core/models/print-request.model';
import { LINK, REQUEST, UPLOADED_FILE } from '../../../../../testing/fixtures';
import { PrintRequestsService } from '../../print-requests.service';
import { ItemStatusControl } from './item-status';

describe('ItemStatusControl', () => {
  const requestsService = { changeItemStatus: vi.fn() };
  let control: ItemStatusControl;
  let changed: PrintRequest[];
  let answer: Subject<PrintRequest>;

  function create(item: RequestFile = UPLOADED_FILE, isAdmin = true): ItemStatusControl {
    const fixture = TestBed.createComponent(ItemStatusControl);
    fixture.componentRef.setInput('requestId', 4);
    fixture.componentRef.setInput('item', item);
    fixture.componentRef.setInput('isAdmin', isAdmin);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.changed.subscribe((request) => {
      changed.push(request);
    });
    return component;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    changed = [];
    answer = new Subject<PrintRequest>();
    requestsService.changeItemStatus.mockReturnValue(answer);
    TestBed.configureTestingModule({
      providers: [{ provide: PrintRequestsService, useValue: requestsService }],
    });
  });

  it('saves a status right away and hands the request to the page', () => {
    control = create();
    control['choose']('ACCEPTED');

    expect(requestsService.changeItemStatus).toHaveBeenCalledWith(4, 7, 'ACCEPTED', null);
    expect(control['saving']()).toBe(true);
    answer.next(REQUEST);
    expect(control['saving']()).toBe(false);
    expect(changed).toEqual([REQUEST]);
  });

  it('asks before declining, with an optional trimmed reason', () => {
    control = create(LINK);
    control['choose']('DECLINED');
    expect(control['confirmingDecline']()).toBe(true);
    expect(requestsService.changeItemStatus).not.toHaveBeenCalled();

    control['setReason']({ target: { value: '  Not on Patreon any more ' } } as unknown as Event);
    control['confirmDecline']();
    expect(requestsService.changeItemStatus).toHaveBeenCalledWith(
      4,
      9,
      'DECLINED',
      'Not on Patreon any more',
    );
    answer.next(REQUEST);
    expect(control['confirmingDecline']()).toBe(false);
  });

  it('declines without a reason when none is given, and can be cancelled', () => {
    control = create();
    control['choose']('DECLINED');
    control['cancelDecline']();
    expect(control['confirmingDecline']()).toBe(false);

    control['choose']('DECLINED');
    expect(control['reason']()).toBe('');
    control['setReason']({ target: { value: '   ' } } as unknown as Event);
    control['confirmDecline']();
    expect(requestsService.changeItemStatus).toHaveBeenCalledWith(4, 7, 'DECLINED', null);
  });

  it('shows why a change was refused', () => {
    requestsService.changeItemStatus.mockReturnValue(
      throwError(() => {
        return new HttpErrorResponse({
          status: 409,
          error: { message: 'This request is COMPLETED and its items can no longer change' },
        });
      }),
    );
    control = create();
    control['choose']('ACCEPTED');

    expect(control['error']()).toBe('This request is COMPLETED and its items can no longer change');
    expect(control['saving']()).toBe(false);
    expect(changed).toEqual([]);
    control['dismissError']();
    expect(control['error']()).toBeNull();
  });

  it('labels the status for everyone', () => {
    control = create({ ...UPLOADED_FILE, status: 'PRINTING' }, false);
    expect(control['label']('PRINTING')).toBe('Printing');
    expect(control['badgeClass']('PRINTING')).toBe('text-bg-warning');
  });
});
