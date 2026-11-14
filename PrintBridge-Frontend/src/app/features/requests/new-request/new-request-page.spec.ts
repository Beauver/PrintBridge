import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { REQUEST, TRIANGLE_MESH } from '../../../core/testing';
import { ModelPreviewService } from '../model-preview.service';
import { PrintRequestsService, UploadEvent } from '../print-requests.service';
import { FileEntry, NewRequestPage } from './new-request-page';

describe('NewRequestPage', () => {
  let page: NewRequestPage;
  let router: Router;
  const requestsService = { uploadConfig: vi.fn(), create: vi.fn(), addFile: vi.fn() };
  const previewService = { uploadMesh: vi.fn() };
  const MB = 1024 * 1024;

  function stl(size = 10, name = 'cube.stl'): File {
    return new File([new Uint8Array(size)], name);
  }

  function select(...files: File[]): HTMLInputElement {
    const input = { files, value: 'C:\\fakepath\\x' } as unknown as HTMLInputElement;
    page['onFilesSelected']({ target: input } as unknown as Event);
    return input;
  }

  function entries(): FileEntry[] {
    return page['entries']();
  }

  function setQuantity(index: number, value: string): void {
    const input = { value } as HTMLInputElement;
    page['setQuantity'](entries()[index].key, { target: input } as unknown as Event);
  }

  beforeEach(() => {
    vi.clearAllMocks();
    requestsService.uploadConfig.mockReturnValue(
      of({ maxSizeBytes: MB, maxFiles: 3, allowedExtensions: ['stl', '3mf'] }),
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

  describe('upload config', () => {
    it('loads limits and builds the accept list and limits text', () => {
      expect(page['config']().maxFiles).toBe(3);
      expect(page['accept']()).toBe('.stl,.3mf');
      expect(page['limits']()).toBe('0 of 3 files · max 1.0 MB each');
      select(stl());
      expect(page['limits']()).toBe('1 of 3 files · max 1.0 MB each');
    });

    it('keeps the fallback when loading fails', () => {
      requestsService.uploadConfig.mockReturnValue(
        throwError(() => {
          return new Error('down');
        }),
      );
      page = TestBed.createComponent(NewRequestPage).componentInstance;
      page.ngOnInit();
      expect(page['config']()).toEqual({
        maxSizeBytes: 0,
        maxFiles: 0,
        allowedExtensions: ['stl', '3mf'],
      });
      expect(page['limits']()).toBe('');
      select(stl(50 * MB), stl(), stl(), stl());
      expect(page['hasFileErrors']()).toBe(false);
    });

    it('checks files chosen before the limits arrived again', () => {
      const config = new Subject<unknown>();
      requestsService.uploadConfig.mockReturnValue(config);
      page = TestBed.createComponent(NewRequestPage).componentInstance;
      page.ngOnInit();
      select(stl(2 * MB));
      expect(entries()[0].error).toBeNull();

      config.next({ maxSizeBytes: MB, maxFiles: 3, allowedExtensions: ['stl'] });
      expect(entries()[0].error).toBe('The file is larger than 1.0 MB');
      expect(entries()[0].loader).toBeNull();
    });
  });

  describe('file list', () => {
    it('adds chosen files with one copy each and resets the input', () => {
      const input = select(stl(10, 'a.stl'), stl(10, 'b.3mf'));
      select(stl(10, 'c.stl'));

      expect(
        entries().map((entry) => {
          return [entry.file.name, entry.quantity, entry.status];
        }),
      ).toEqual([
        ['a.stl', 1, 'waiting'],
        ['b.3mf', 1, 'waiting'],
        ['c.stl', 1, 'waiting'],
      ]);
      expect(input.value).toBe('');
      expect(page['hasFileErrors']()).toBe(false);
    });

    it('handles a selection without files', () => {
      page['onFilesSelected']({ target: { files: null, value: '' } } as unknown as Event);
      expect(entries()).toEqual([]);
    });

    it('checks each file', () => {
      select(stl(10, 'model.obj'), stl(10, 'noextension'), stl(0), stl(MB + 1), stl());
      expect(
        entries().map((entry) => {
          return entry.error;
        }),
      ).toEqual([
        'Only .stl, .3mf files are allowed',
        'Only .stl, .3mf files are allowed',
        'The file is empty',
        'The file is larger than 1.0 MB',
        null,
      ]);
      expect(page['hasFileErrors']()).toBe(true);
    });

    it('refuses more files than the limit', () => {
      select(stl(), stl(), stl());
      expect(page['filesError']()).toBeNull();
      select(stl());
      expect(page['filesError']()).toBe('A request can hold at most 3 files');
      expect(page['hasFileErrors']()).toBe(true);
    });

    it('removes a file', () => {
      select(stl(10, 'a.stl'), stl(10, 'b.stl'));
      page['removeEntry'](entries()[0].key);
      expect(
        entries().map((entry) => {
          return entry.file.name;
        }),
      ).toEqual(['b.stl']);
    });

    it('sets quantities and flags invalid ones', () => {
      select(stl(), stl());
      setQuantity(1, '4');
      expect(entries()[1].quantity).toBe(4);
      expect(page['hasFileErrors']()).toBe(false);

      setQuantity(0, '0');
      expect(page['hasFileErrors']()).toBe(true);
      setQuantity(0, '101');
      expect(page['hasFileErrors']()).toBe(true);
      setQuantity(0, '1.5');
      expect(page['hasFileErrors']()).toBe(true);
      setQuantity(0, '100');
      expect(page['hasFileErrors']()).toBe(false);
    });

    it('offers a preview of usable files only, and keeps it on changes', () => {
      const file = stl();
      select(stl(10, 'model.obj'), file);
      expect(entries()[0].loader).toBeNull();

      const loader = entries()[1].loader;
      previewService.uploadMesh.mockReturnValue(of(TRIANGLE_MESH));
      loader!().subscribe();
      expect(previewService.uploadMesh).toHaveBeenCalledWith(file);

      setQuantity(1, '2');
      expect(entries()[1].loader).toBe(loader);
    });
  });

  describe('submitting', () => {
    let create: Subject<UploadEvent>;
    let adds: Subject<UploadEvent>[];

    beforeEach(() => {
      create = new Subject<UploadEvent>();
      adds = [];
      requestsService.create.mockReturnValue(create);
      requestsService.addFile.mockImplementation(() => {
        const add = new Subject<UploadEvent>();
        adds.push(add);
        return add;
      });
      page['form'].setValue({ name: 'Cube', description: 'Small' });
    });

    it('requires a file', () => {
      page['submit']();
      expect(page['filesError']()).toBe('Add at least one model file');
      expect(requestsService.create).not.toHaveBeenCalled();
    });

    it('does not submit an invalid form or a file with an error', () => {
      select(stl());
      page['form'].controls.name.setValue('');
      page['submit']();
      page['form'].controls.name.setValue('Cube');
      select(stl(10, 'a.obj'));
      page['submit']();
      expect(requestsService.create).not.toHaveBeenCalled();
    });

    it('uploads one file with progress and opens the new request', () => {
      select(stl());
      setQuantity(0, '2');
      page['submit']();
      expect(requestsService.create).toHaveBeenCalledWith({
        name: 'Cube',
        description: 'Small',
        file: entries()[0].file,
        quantity: 2,
      });
      expect(page['uploading']()).toBe(true);
      expect(page['uploadingText']()).toBe('Uploading…');

      create.next({ type: 'progress', percent: 40 });
      expect(entries()[0].progress).toBe(40);

      create.next({ type: 'done', request: REQUEST });
      expect(entries()[0].status).toBe('done');
      expect(page['uploading']()).toBe(false);
      expect(requestsService.addFile).not.toHaveBeenCalled();
      expect(router.navigate).toHaveBeenCalledWith(['/requests', 4]);
    });

    it('creates with the first file, then adds the others one by one', () => {
      select(stl(10, 'a.stl'), stl(10, 'b.stl'), stl(10, 'c.stl'));
      setQuantity(2, '5');
      page['submit']();
      create.next({ type: 'done', request: REQUEST });

      expect(requestsService.addFile).toHaveBeenCalledTimes(1);
      expect(requestsService.addFile).toHaveBeenCalledWith(4, entries()[1].file, 1);
      expect(page['uploadingText']()).toBe('Uploading 2 of 3…');
      adds[0].next({ type: 'progress', percent: 60 });
      expect(entries()[1].progress).toBe(60);
      adds[0].next({ type: 'done', request: REQUEST });

      expect(requestsService.addFile).toHaveBeenLastCalledWith(4, entries()[2].file, 5);
      adds[1].next({ type: 'done', request: REQUEST });

      expect(
        entries().map((entry) => {
          return entry.status;
        }),
      ).toEqual(['done', 'done', 'done']);
      expect(router.navigate).toHaveBeenCalledWith(['/requests', 4]);
    });

    it('goes on after a failed extra file and hands the failures to the request page', () => {
      select(stl(10, 'a.stl'), stl(10, 'b.stl'), stl(10, 'c.stl'));
      page['submit']();
      create.next({ type: 'done', request: REQUEST });
      adds[0].error(
        new HttpErrorResponse({
          status: 400,
          error: { message: 'The file is not a valid STL file' },
        }),
      );
      expect(entries()[1].status).toBe('failed');
      adds[1].error(new HttpErrorResponse({ status: 0 }));

      expect(router.navigate).toHaveBeenCalledWith(['/requests', 4], {
        state: {
          failedUploads: [
            { filename: 'b.stl', reason: 'The file is not a valid STL file' },
            { filename: 'c.stl', reason: 'Cannot reach the server.' },
          ],
        },
      });
      expect(page['uploading']()).toBe(false);
    });

    it('shows the backend error when the request itself fails', () => {
      select(stl(), stl());
      page['submit']();
      create.error(
        new HttpErrorResponse({
          status: 400,
          error: { message: 'The file is not a valid STL file' },
        }),
      );
      expect(page['uploading']()).toBe(false);
      expect(page['error']()).toBe('The file is not a valid STL file');
      expect(entries()[0].status).toBe('waiting');
      expect(requestsService.addFile).not.toHaveBeenCalled();
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('explains a 413 from the server', () => {
      select(stl());
      page['submit']();
      create.error(new HttpErrorResponse({ status: 413 }));
      expect(page['error']()).toBe('The file is larger than 1.0 MB');
    });

    it('explains a 413 without a known limit', () => {
      page['config'].set({ maxSizeBytes: 0, maxFiles: 0, allowedExtensions: ['stl'] });
      select(stl());
      page['submit']();
      create.error(new HttpErrorResponse({ status: 413 }));
      expect(page['error']()).toBe('The file is too large');
    });
  });
});
