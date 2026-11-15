import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { REQUEST } from '../../../testing/fixtures';
import { PrintRequestsService, UploadEvent } from './print-requests.service';
import { BatchUploadEvent, RequestUploadService } from './request-upload.service';

describe('RequestUploadService', () => {
  const requestsService = { create: vi.fn(), addFile: vi.fn() };
  let service: RequestUploadService;
  let creation: Subject<UploadEvent>;
  let additions: Subject<UploadEvent>[];
  let events: BatchUploadEvent[];
  let failure: unknown;
  let completed: boolean;

  function stl(name: string): File {
    return new File([new Uint8Array(10)], name);
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

  describe('createWithFiles', () => {
    const first = { file: stl('a.stl'), quantity: 2 };
    const second = { file: stl('b.stl'), quantity: 1 };
    const third = { file: stl('c.stl'), quantity: 5 };

    it('creates the request with the first file, then adds the others to it in order', () => {
      service.createWithFiles('Cube', 'Small', [first, second, third]).subscribe(observer());
      expect(requestsService.create).toHaveBeenCalledWith({
        name: 'Cube',
        description: 'Small',
        file: first.file,
        quantity: 2,
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
      service.createWithFiles('Cube', '', [first, second]).subscribe(observer());
      creation.error('too large');
      expect(failure).toBe('too large');
      expect(requestsService.addFile).not.toHaveBeenCalled();
      expect(completed).toBe(false);
    });

    it('reports a later file that fails and goes on with the next', () => {
      service.createWithFiles('Cube', '', [first, second, third]).subscribe(observer());
      creation.next({ type: 'done', request: REQUEST });
      additions[0].error('broken');
      expect(events).toContainEqual({ type: 'failed', index: 1, error: 'broken' });
      expect(failure).toBeNull();

      additions[1].next({ type: 'done', request: REQUEST });
      expect(completed).toBe(true);
    });

    it('stops when unsubscribed: the running upload is cancelled, the rest never starts', () => {
      const subscription = service
        .createWithFiles('Cube', '', [first, second, third])
        .subscribe(observer());
      creation.next({ type: 'done', request: REQUEST });
      expect(additions[0].observed).toBe(true);

      subscription.unsubscribe();
      expect(additions[0].observed).toBe(false);
      additions[0].next({ type: 'done', request: REQUEST });
      expect(requestsService.addFile).toHaveBeenCalledTimes(1);
    });

    it('does nothing until subscribed', () => {
      service.createWithFiles('Cube', '', [first]);
      expect(requestsService.create).not.toHaveBeenCalled();
    });
  });

  describe('addFiles', () => {
    it('adds the files one at a time and reports failures without stopping', () => {
      const files = [
        { file: stl('a.stl'), quantity: 1 },
        { file: stl('b.stl'), quantity: 3 },
      ];
      service.addFiles(4, files).subscribe(observer());
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

    it('completes right away without files', () => {
      service.addFiles(4, []).subscribe(observer());
      expect(completed).toBe(true);
      expect(requestsService.addFile).not.toHaveBeenCalled();
    });
  });
});
