import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { REQUEST, TRIANGLE_MESH } from '../../../core/testing';
import { ModelPreviewService } from '../model-preview.service';
import { PrintRequestsService, UploadEvent } from '../print-requests.service';
import { NewRequestPage } from './new-request-page';

describe('NewRequestPage', () => {
  let page: NewRequestPage;
  let router: Router;
  const requestsService = { uploadConfig: vi.fn(), create: vi.fn() };
  const previewService = { uploadMesh: vi.fn() };
  const MB = 1024 * 1024;

  function stl(size = 10, name = 'cube.stl'): File {
    return new File([new Uint8Array(size)], name);
  }

  function select(file: File | null): void {
    const files: File[] = [];
    if (file) {
      files.push(file);
    }
    page['onFileSelected']({ target: { files } } as unknown as Event);
  }

  beforeEach(() => {
    vi.clearAllMocks();
    requestsService.uploadConfig.mockReturnValue(
      of({ maxSizeBytes: MB, allowedExtensions: ['stl', '3mf'] }),
    );
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: PrintRequestsService, useValue: requestsService },
        { provide: ModelPreviewService, useValue: previewService },
      ],
    });
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    page = TestBed.createComponent(NewRequestPage).componentInstance;
    page.ngOnInit();
  });

  describe('preview before uploading', () => {
    it('offers a preview of a usable file only', () => {
      expect(page['previewLoader']()).toBeNull();

      select(stl(10, 'model.obj'));
      expect(page['previewLoader']()).toBeNull();

      const file = stl();
      select(file);
      previewService.uploadMesh.mockReturnValue(of(TRIANGLE_MESH));
      page['previewLoader']()!().subscribe();
      expect(previewService.uploadMesh).toHaveBeenCalledWith(file);
    });
  });

  describe('upload config', () => {
    it('loads limits and builds the accept list', () => {
      expect(page['config']().maxSizeBytes).toBe(MB);
      expect(page['accept']()).toBe('.stl,.3mf');
    });

    it('keeps the fallback when loading fails', () => {
      requestsService.uploadConfig.mockReturnValue(
        throwError(() => {
          return new Error('down');
        }),
      );
      const other = TestBed.createComponent(NewRequestPage).componentInstance;
      other.ngOnInit();
      expect(other['config']()).toEqual({ maxSizeBytes: 0, allowedExtensions: ['stl', '3mf'] });
      other['file'].set(stl(50 * MB));
      expect(other['fileError']()).toBeNull();
    });
  });

  describe('file checks', () => {
    it('has no error before a file is chosen and the form was not submitted', () => {
      expect(page['fileError']()).toBeNull();
    });

    it('requires a file after submitting', () => {
      page['form'].setValue({ name: 'Cube', description: '' });
      page['submit']();
      expect(page['fileError']()).toBe('A model file is required');
      expect(requestsService.create).not.toHaveBeenCalled();
    });

    it('accepts a valid file', () => {
      select(stl(10, 'Cube.STL'));
      expect(page['file']()?.name).toBe('Cube.STL');
      expect(page['fileError']()).toBeNull();
    });

    it('rejects other extensions, empty and too large files', () => {
      select(stl(10, 'model.obj'));
      expect(page['fileError']()).toBe('Only .stl, .3mf files are allowed');
      select(stl(10, 'noextension'));
      expect(page['fileError']()).toBe('Only .stl, .3mf files are allowed');
      select(stl(0));
      expect(page['fileError']()).toBe('The file is empty');
      select(stl(MB + 1));
      expect(page['fileError']()).toBe('The file is larger than 1.0 MB');
    });

    it('clears the file when the selection is emptied', () => {
      select(stl());
      select(null);
      expect(page['file']()).toBeNull();
    });
  });

  describe('submitting', () => {
    let events: Subject<UploadEvent>;

    beforeEach(() => {
      events = new Subject<UploadEvent>();
      requestsService.create.mockReturnValue(events);
      page['form'].setValue({ name: 'Cube', description: 'Small' });
      select(stl());
    });

    it('does not submit an invalid form', () => {
      page['form'].controls.name.setValue('');
      page['submit']();
      expect(requestsService.create).not.toHaveBeenCalled();
    });

    it('does not submit a file with an error', () => {
      select(stl(10, 'a.obj'));
      page['submit']();
      expect(requestsService.create).not.toHaveBeenCalled();
    });

    it('uploads with progress and opens the new request', () => {
      page['submit']();
      expect(requestsService.create).toHaveBeenCalledWith({
        name: 'Cube',
        description: 'Small',
        file: page['file'](),
      });
      expect(page['uploading']()).toBe(true);

      events.next({ type: 'progress', percent: 40 });
      expect(page['progress']()).toBe(40);

      events.next({ type: 'done', request: REQUEST });
      expect(page['uploading']()).toBe(false);
      expect(router.navigate).toHaveBeenCalledWith(['/requests', 4]);
    });

    it('shows the backend error', () => {
      page['submit']();
      events.error(
        new HttpErrorResponse({
          status: 400,
          error: { message: 'The file is not a valid STL file' },
        }),
      );
      expect(page['uploading']()).toBe(false);
      expect(page['error']()).toBe('The file is not a valid STL file');
    });

    it('explains a 413 from the server', () => {
      page['submit']();
      events.error(new HttpErrorResponse({ status: 413 }));
      expect(page['error']()).toBe('The file is larger than 1.0 MB');
    });

    it('explains a 413 without a known limit', () => {
      page['config'].set({ maxSizeBytes: 0, allowedExtensions: ['stl'] });
      page['submit']();
      events.error(new HttpErrorResponse({ status: 413 }));
      expect(page['error']()).toBe('The file is too large');
    });
  });
});
