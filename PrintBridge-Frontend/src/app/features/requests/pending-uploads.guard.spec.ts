import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { LEAVE_WHILE_UPLOADING, pendingUploadsGuard } from './pending-uploads.guard';

describe('pendingUploadsGuard', () => {
  const confirm = vi.fn();

  function leave(uploading: boolean): unknown {
    const page = {
      hasPendingUploads: () => {
        return uploading;
      },
    };
    return TestBed.runInInjectionContext(() => {
      return pendingUploadsGuard(
        page,
        {} as ActivatedRouteSnapshot,
        {} as RouterStateSnapshot,
        {} as RouterStateSnapshot,
      );
    });
  }

  function provideView(view: { confirm: typeof confirm } | null): void {
    TestBed.configureTestingModule({
      providers: [{ provide: DOCUMENT, useValue: { defaultView: view } }],
    });
  }

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('lets the user leave without asking when nothing is uploading', () => {
    provideView({ confirm });
    expect(leave(false)).toBe(true);
    expect(confirm).not.toHaveBeenCalled();
  });

  it('asks while files are uploading and follows the answer', () => {
    provideView({ confirm });
    confirm.mockReturnValue(false);
    expect(leave(true)).toBe(false);
    expect(confirm).toHaveBeenCalledWith(LEAVE_WHILE_UPLOADING);

    confirm.mockReturnValue(true);
    expect(leave(true)).toBe(true);
  });

  it('does not block where there is no window to ask in', () => {
    provideView(null);
    expect(leave(true)).toBe(true);
  });
});
