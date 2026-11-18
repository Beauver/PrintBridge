import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { NewEntry } from '../../core/models/print-request.model';
import { REQUEST } from '../../../testing/fixtures';
import { PrintRequestsService, UploadEvent } from './print-requests.service';
import { BatchUploadEvent, RequestUploadService } from './request-upload.service';

describe('RequestUploadService', () => {
  const requestsService = { create: vi.fn(), addFile: vi.fn(), addLink: vi.fn() };
  let service: RequestUploadService;
  let creation: Subject<UploadEvent>;
  let additions: Subject<UploadEvent>[];
  let events: BatchUploadEvent[];
  let failure: unknown;
  let completed: boolean;

  function stl(name: string): File {
    return new File([new Uint8Array(10)], name);
  }

  function fileEntry(name: string, quantity: number): NewEntry & { kind: 'file' } {
    return { kind: 'file', file: stl(name), quantity };
  }

  function observer() {
    return {
      next: (event: BatchUploadEvent) => {
        events.push(event);
      },
      error: (error: unknown) => {
        failure = error;
      },
      complete: () => {
        completed = true;
      },
    };
  }

  beforeEach(() => {
    vi.clearAllMocks();
    creation = new Subject<UploadEvent>();
    additions = [];
    events = [];
    failure = null;
    completed = false;
    requestsService.create.mockReturnValue(creation);
    requestsService.addFile.mockImplementation(() => {
      const addition = new Subject<UploadEvent>();
      additions.push(addition);
      return addition;
    });
    TestBed.configureTestingModule({
      providers: [{ provide: PrintRequestsService, useValue: requestsService }],
    });
    service = TestBed.inject(RequestUploadService);
  });

  describe('createWithEntries', () => {
    const first = fileEntry('a.stl', 2);
    const second = fileEntry('b.stl', 1);
    const third = fileEntry('c.stl', 5);

    it('creates the request with the first file, then adds the others to it in order', () => {
      service.createWithEntries('Cube', 'Small', [first, second, third]).subscribe(observer());
      expect(requestsService.create).toHaveBeenCalledWith({
        name: 'Cube',
        description: 'Small',
        first,
      });
      expect(requestsService.addFile).not.toHaveBeenCalled();

      creation.next({ type: 'progress', percent: 50 });
      creation.next({ type: 'done', request: REQUEST });
      expect(requestsService.addFile).toHaveBeenCalledTimes(1);
      expect(requestsService.addFile).toHaveBeenCalledWith(REQUEST.id, second.file, 1);

      additions[0].next({ type: 'done', request: REQUEST });
      expect(requestsService.addFile).toHaveBeenLastCalledWith(REQUEST.id, third.file, 5);
      expect(completed).toBe(false);

      additions[1].next({ type: 'done', request: REQUEST });
      expect(completed).toBe(true);
      expect(events).toEqual([
        { type: 'started', index: 0 },
        { type: 'progress', index: 0, percent: 50 },
        { type: 'uploaded', index: 0, request: REQUEST },
        { type: 'started', index: 1 },
        { type: 'uploaded', index: 1, request: REQUEST },
        { type: 'started', index: 2 },
        { type: 'uploaded', index: 2, request: REQUEST },
      ]);
    });

    it('errors when the first file fails and uploads nothing else', () => {
      service.createWithEntries('Cube', '', [first, second]).subscribe(observer());
      creation.error('too large');
      expect(failure).toBe('too large');
      expect(requestsService.addFile).not.toHaveBeenCalled();
      expect(completed).toBe(false);
    });

    it('reports a later file that fails and goes on with the next', () => {
      service.createWithEntries('Cube', '', [first, second, third]).subscribe(observer());
      creation.next({ type: 'done', request: REQUEST });
      additions[0].error('broken');
      expect(events).toContainEqual({ type: 'failed', index: 1, error: 'broken' });
      expect(failure).toBeNull();

      additions[1].next({ type: 'done', request: REQUEST });
      expect(completed).toBe(true);
    });

    it('stops when unsubscribed: the running upload is cancelled, the rest never starts', () => {
      const subscription = service
        .createWithEntries('Cube', '', [first, second, third])
        .subscribe(observer());
      creation.next({ type: 'done', request: REQUEST });
      expect(additions[0].observed).toBe(true);

      subscription.unsubscribe();
      expect(additions[0].observed).toBe(false);
      additions[0].next({ type: 'done', request: REQUEST });
      expect(requestsService.addFile).toHaveBeenCalledTimes(1);
    });

    it('adds a link after the first file, without progress', () => {
      const link: NewEntry = {
        kind: 'link',
        link: { url: 'https://example.com/a', name: '' },
        quantity: 2,
      };
      requestsService.addLink.mockReturnValue(of(REQUEST));
      service.createWithEntries('Cube', '', [first, link]).subscribe(observer());
      creation.next({ type: 'done', request: REQUEST });

      expect(requestsService.addLink).toHaveBeenCalledWith(
        REQUEST.id,
        { url: 'https://example.com/a', name: '' },
        2,
      );
      expect(events.slice(-2)).toEqual([
        { type: 'started', index: 1 },
        { type: 'uploaded', index: 1, request: REQUEST },
      ]);
      expect(completed).toBe(true);
    });

    it('does nothing until subscribed', () => {
      service.createWithEntries('Cube', '', [first]);
      expect(requestsService.create).not.toHaveBeenCalled();
    });
  });

  describe('addEntries', () => {
    it('adds the files one at a time and reports failures without stopping', () => {
      const files = [fileEntry('a.stl', 1), fileEntry('b.stl', 3)];
      service.addEntries(4, files).subscribe(observer());
      expect(requestsService.addFile).toHaveBeenCalledTimes(1);
      expect(requestsService.addFile).toHaveBeenCalledWith(4, files[0].file, 1);

      additions[0].next({ type: 'progress', percent: 10 });
      additions[0].error('nope');
      expect(requestsService.addFile).toHaveBeenLastCalledWith(4, files[1].file, 3);

      additions[1].next({ type: 'done', request: REQUEST });
      expect(completed).toBe(true);
      expect(events).toEqual([
        { type: 'started', index: 0 },
        { type: 'progress', index: 0, percent: 10 },
        { type: 'failed', index: 0, error: 'nope' },
        { type: 'started', index: 1 },
        { type: 'uploaded', index: 1, request: REQUEST },
      ]);
    });

    it('reports a link that fails and goes on', () => {
      const link: NewEntry = {
        kind: 'link',
        link: { url: 'https://example.com/a', name: 'A' },
        quantity: 1,
      };
      requestsService.addLink.mockReturnValue(
        throwError(() => {
          return 'refused';
        }),
      );
      service.addEntries(4, [link, fileEntry('b.stl', 1)]).subscribe(observer());

      expect(events).toContainEqual({ type: 'failed', index: 0, error: 'refused' });
      expect(requestsService.addFile).toHaveBeenCalledTimes(1);
    });

    it('completes right away without files', () => {
      service.addEntries(4, []).subscribe(observer());
      expect(completed).toBe(true);
      expect(requestsService.addFile).not.toHaveBeenCalled();
    });
  });
});
