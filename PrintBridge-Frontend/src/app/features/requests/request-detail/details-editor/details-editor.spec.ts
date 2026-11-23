import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { PrintRequest } from '../../../../core/models/print-request.model';
import { REQUEST } from '../../../../../testing/fixtures';
import { PrintRequestsService } from '../../print-requests.service';
import { DetailsEditor } from './details-editor';

describe('DetailsEditor', () => {
  const requestsService = { changeDetails: vi.fn() };
  let saved: PrintRequest[];
  let cancelled: number;

  function create(request: PrintRequest = REQUEST): DetailsEditor {
    const fixture = TestBed.createComponent(DetailsEditor);
    fixture.componentRef.setInput('request', request);
    const editor = fixture.componentInstance;
    editor.saved.subscribe((updated) => {
      saved.push(updated);
    });
    editor.cancelled.subscribe(() => {
      cancelled++;
    });
    editor.ngOnInit();
    return editor;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    saved = [];
    cancelled = 0;
    TestBed.configureTestingModule({
      providers: [{ provide: PrintRequestsService, useValue: requestsService }],
    });
  });

  it('starts with the current name and description', () => {
    const editor = create({ ...REQUEST, name: 'Benchy', description: 'In red' });
    expect(editor['form'].getRawValue()).toEqual({ name: 'Benchy', description: 'In red' });
    const withoutDescription = create({ ...REQUEST, description: null });
    expect(withoutDescription['form'].getRawValue().description).toBe('');
  });

  it('saves the trimmed name and the description', () => {
    const updated = { ...REQUEST, name: 'Big benchy', description: null };
    requestsService.changeDetails.mockReturnValue(of(updated));
    const editor = create();
    editor['form'].setValue({ name: '  Big benchy ', description: '' });

    editor['save']();

    expect(requestsService.changeDetails).toHaveBeenCalledWith(REQUEST.id, 'Big benchy', '');
    expect(saved).toEqual([updated]);
    expect(editor['saving']()).toBe(false);
    expect(editor['error']()).toBeNull();
  });

  it.each([
    ['  ', '', 'Name is required'],
    ['x'.repeat(101), '', 'Name must be at most 100 characters'],
    ['Ok', 'x'.repeat(2001), 'Description must be at most 2000 characters'],
  ])('refuses name %#', (name, description, message) => {
    const editor = create();
    editor['form'].setValue({ name, description });

    editor['save']();

    expect(editor['error']()).toBe(message);
    expect(requestsService.changeDetails).not.toHaveBeenCalled();
  });

  it('shows why saving failed', () => {
    requestsService.changeDetails.mockReturnValue(
      throwError(() => {
        return new HttpErrorResponse({
          status: 409,
          error: {
            message: 'The name and description can only be changed while the request is submitted',
          },
        });
      }),
    );
    const editor = create();

    editor['save']();

    expect(editor['error']()).toBe(
      'The name and description can only be changed while the request is submitted',
    );
    expect(editor['saving']()).toBe(false);
    expect(saved).toEqual([]);
  });

  it('cancels without saving', () => {
    const editor = create();
    editor['cancel']();
    expect(cancelled).toBe(1);
    expect(requestsService.changeDetails).not.toHaveBeenCalled();
  });
});
