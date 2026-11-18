import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { PrintRequest } from '../../../../core/models/print-request.model';
import { REQUEST } from '../../../../../testing/fixtures';
import { PrintRequestsService, UploadEvent } from '../../print-requests.service';
import { AddFiles } from './add-files';

describe('AddFiles', () => {
  const requestsService = { uploadConfig: vi.fn(), addFile: vi.fn(), addLink: vi.fn() };
  let card: AddFiles;
  let added: PrintRequest[];
  let uploads: Subject<UploadEvent>[];

  function stl(size = 10, name = 'a.stl'): File {
    return new File([new Uint8Array(size)], name);
  }

  function create(fileCount = 1): AddFiles {
    const fixture = TestBed.createComponent(AddFiles);
    fixture.componentRef.setInput('requestId', 4);
    fixture.componentRef.setInput('fileCount', fileCount);
    const component = fixture.componentInstance;
    component.added.subscribe((request) => {
      added.push(request);
    });
    component.ngOnInit();
    return component;
  }

  function select(...files: File[]): HTMLInputElement {
    const input = { files, value: 'x' } as unknown as HTMLInputElement;
    card['onFilesSelected']({ target: input } as unknown as Event);
    return input;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    added = [];
    uploads = [];
    requestsService.uploadConfig.mockReturnValue(
      of({ maxSizeBytes: 1024, maxFiles: 3, allowedExtensions: ['stl'] }),
    );
    requestsService.addFile.mockImplementation(() => {
      const upload = new Subject<UploadEvent>();
      uploads.push(upload);
      return upload;
    });
    TestBed.configureTestingModule({
      providers: [{ provide: PrintRequestsService, useValue: requestsService }],
    });
    card = create();
  });

  it('shows the limits and the accept list', () => {
    expect(card['limits']()).toBe('1 of 3 files · max 1.0 KB each');
    expect(card['accept']()).toBe('.stl');
  });

  it('keeps the fallback when the config fails to load', () => {
    requestsService.uploadConfig.mockReturnValue(
      throwError(() => {
        return new Error('down');
      }),
    );
    card = create(9);
    expect(card['limits']()).toBe('');
    select(stl(), stl());
    expect(card['queue']().length).toBe(2);
  });

  it('uploads the chosen files one by one with one copy each', () => {
    const first = stl(10, 'a.stl');
    const second = stl(10, 'b.stl');
    const input = select(first, second);
    expect(input.value).toBe('');
    expect(card['busy']()).toBe(true);
    expect(requestsService.addFile).toHaveBeenCalledTimes(1);
    expect(requestsService.addFile).toHaveBeenCalledWith(4, first, 1);

    uploads[0].next({ type: 'progress', percent: 30 });
    expect(card['queue']()[0].progress).toBe(30);
    expect(card['queue']()[1].progress).toBe(0);
    uploads[0].next({ type: 'done', request: REQUEST });
    expect(added).toEqual([REQUEST]);
    expect(requestsService.addFile).toHaveBeenLastCalledWith(4, second, 1);

    uploads[1].next({ type: 'done', request: REQUEST });
    expect(added.length).toBe(2);
    expect(card['busy']()).toBe(false);
    expect(card['failed']()).toEqual([]);
  });

  it('lists files that fail the checks or do not fit, and uploads the rest', () => {
    select(
      stl(10, 'a.obj'),
      stl(0, 'empty.stl'),
      stl(10, 'b.stl'),
      stl(10, 'c.stl'),
      stl(10, 'd.stl'),
    );

    expect(card['failed']()).toEqual([
      { filename: 'a.obj', reason: 'Only .stl files are allowed' },
      { filename: 'empty.stl', reason: 'The file is empty' },
      { filename: 'd.stl', reason: 'A request can hold at most 3 files and links' },
    ]);
    expect(
      card['queue']().map((upload) => {
        return upload.name;
      }),
    ).toEqual(['b.stl', 'c.stl']);
  });

  it('lists an upload the server refused and goes on', () => {
    select(stl(10, 'a.stl'), stl(10, 'b.stl'));
    uploads[0].error(new HttpErrorResponse({ status: 413 }));
    expect(card['failed']()).toEqual([
      { filename: 'a.stl', reason: 'The file is larger than 1.0 KB' },
    ]);
    expect(requestsService.addFile).toHaveBeenCalledTimes(2);

    card['dismissFailed']();
    expect(card['failed']()).toEqual([]);
  });

  it('does nothing for an empty selection', () => {
    card['onFilesSelected']({ target: { files: null, value: '' } } as unknown as Event);
    expect(requestsService.addFile).not.toHaveBeenCalled();
    expect(card['busy']()).toBe(false);
  });

  it('cancels the remaining uploads and emits nothing once it is destroyed', () => {
    const fixture = TestBed.createComponent(AddFiles);
    fixture.componentRef.setInput('requestId', 4);
    fixture.componentRef.setInput('fileCount', 1);
    const leaving = fixture.componentInstance;
    leaving.ngOnInit();
    const input = { files: [stl(), stl(10, 'b.stl')], value: 'x' } as unknown as HTMLInputElement;
    leaving['onFilesSelected']({ target: input } as unknown as Event);
    expect(leaving.busy()).toBe(true);

    fixture.destroy();
    expect(uploads[0].observed).toBe(false);
    uploads[0].next({ type: 'done', request: REQUEST });
    expect(requestsService.addFile).toHaveBeenCalledTimes(1);
    expect(added).toEqual([]);
  });

  describe('links (#59)', () => {
    const LINK = { url: 'https://www.printables.com/model/1', name: '' };

    it('opens and closes the link form', () => {
      card['openLinkForm']();
      expect(card['addingLink']()).toBe(true);
      card['closeLinkForm']();
      expect(card['addingLink']()).toBe(false);
    });

    it('adds a link right away with one copy', () => {
      const answer = new Subject<PrintRequest>();
      requestsService.addLink.mockReturnValue(answer);

      card['onLinkAdded'](LINK);

      expect(requestsService.addLink).toHaveBeenCalledWith(4, LINK, 1);
      expect(card['busy']()).toBe(true);
      expect(card['queue']()[0].name).toBe('www.printables.com');
      answer.next(REQUEST);
      expect(added).toEqual([REQUEST]);
      expect(card['busy']()).toBe(false);
    });

    it('lists a link the server refused under its name', () => {
      requestsService.addLink.mockReturnValue(
        throwError(() => {
          return new HttpErrorResponse({ status: 409, error: { message: 'Not any more' } });
        }),
      );
      card['onLinkAdded']({ url: LINK.url, name: 'Lid' });
      expect(card['failed']()).toEqual([{ filename: 'Lid', reason: 'Not any more' }]);
    });

    it('refuses a link when the request is full', () => {
      card = create(3);
      card['onLinkAdded'](LINK);
      expect(requestsService.addLink).not.toHaveBeenCalled();
      expect(card['failed']()).toEqual([
        { filename: 'www.printables.com', reason: 'A request can hold at most 3 files and links' },
      ]);
    });
  });
});
