import {
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  OnInit,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FailedUpload,
  NewEntry,
  NewLink,
  PrintRequest,
  UploadConfig,
} from '../../../../core/models/print-request.model';
import { linkLabel } from '../../../../shared/format/print-request-display';
import { LinkForm } from '../../link-form/link-form';
import { PrintRequestsService } from '../../print-requests.service';
import { RequestUploadService } from '../../request-upload.service';
import {
  checkFile,
  dotted,
  FALLBACK_CONFIG,
  limitsText,
  MIN_QUANTITY,
  uploadErrorMessage,
} from '../../upload-checks';

/** A file or link on its way up. */
export interface PendingUpload {
  key: number;
  /** The file name, or the link's name or host. */
  name: string;
  entry: NewEntry;
  progress: number;
}

/**
 * The "Add files" and "Add link" buttons of a request (#23, #59): checks the chosen files and
 * links against the limits, then adds them one at a time with one copy each. Every addition that
 * succeeds hands the updated request to the page; the ones that fail are listed with the reason.
 */
@Component({
  selector: 'app-add-files',
  imports: [LinkForm],
  templateUrl: './add-files.html',
})
export class AddFiles implements OnInit {
  private readonly requestsService = inject(PrintRequestsService);
  private readonly uploadService = inject(RequestUploadService);
  private readonly destroyRef = inject(DestroyRef);
  private nextKey = 1;

  readonly requestId = input.required<number>();
  /** Files and links the request holds now. */
  readonly fileCount = input.required<number>();
  readonly added = output<PrintRequest>();

  protected readonly config = signal<UploadConfig>(FALLBACK_CONFIG);
  /** Waiting and uploading, in order; the first one is going up. */
  protected readonly queue = signal<PendingUpload[]>([]);
  protected readonly failed = signal<FailedUpload[]>([]);
  /** Whether the "Add link" row is open. */
  protected readonly addingLink = signal(false);

  /** Whether files are waiting or going up; also what the request page asks before leaving. */
  readonly busy = computed(() => {
    return this.queue().length > 0;
  });

  protected readonly accept = computed(() => {
    return dotted(this.config().allowedExtensions).join(',');
  });

  /** "3 of 10 files · max 100 MB each" */
  protected readonly limits = computed(() => {
    return limitsText(this.fileCount(), this.config());
  });

  ngOnInit(): void {
    this.requestsService.uploadConfig().subscribe({
      next: (config) => {
        this.config.set(config);
      },
      error: () => {
        // Keep the fallback; the server still enforces its limits.
      },
    });
  }

  protected onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const chosen = Array.from(input.files ?? []);
    // So choosing the same file again still fires a change.
    input.value = '';
    if (chosen.length === 0) {
      return;
    }
    const failed: FailedUpload[] = [];
    const accepted: PendingUpload[] = [];
    let room = this.room();
    for (const file of chosen) {
      const error = checkFile(file, this.config());
      if (error) {
        failed.push({ filename: file.name, reason: error });
      } else if (room <= 0) {
        failed.push({ filename: file.name, reason: this.limitReason() });
      } else {
        accepted.push(this.pending(file.name, { kind: 'file', file, quantity: MIN_QUANTITY }));
        room--;
      }
    }
    this.failed.set(failed);
    this.queue.set(accepted);
    this.upload(accepted);
  }

  protected openLinkForm(): void {
    this.addingLink.set(true);
  }

  protected closeLinkForm(): void {
    this.addingLink.set(false);
  }

  /** The link form checked the link; it is added right away, with one copy. */
  protected onLinkAdded(link: NewLink): void {
    const name = linkLabel(link.url, link.name);
    if (this.room() <= 0) {
      this.failed.set([{ filename: name, reason: this.limitReason() }]);
      return;
    }
    const accepted = [this.pending(name, { kind: 'link', link, quantity: MIN_QUANTITY })];
    this.failed.set([]);
    this.queue.set(accepted);
    this.upload(accepted);
  }

  protected dismissFailed(): void {
    this.failed.set([]);
  }

  private pending(name: string, entry: NewEntry): PendingUpload {
    const pending: PendingUpload = { key: this.nextKey, name, entry, progress: 0 };
    this.nextKey++;
    return pending;
  }

  private limitReason(): string {
    return `A request can hold at most ${this.config().maxFiles} files and links`;
  }

  /** How many more entries fit; unlimited until the config is known (the backend checks anyway). */
  private room(): number {
    const maxFiles = this.config().maxFiles;
    if (maxFiles <= 0) {
      return Number.MAX_SAFE_INTEGER;
    }
    return maxFiles - this.fileCount();
  }

  /** Sends the accepted files and links one at a time. */
  private upload(accepted: PendingUpload[]): void {
    const entries = accepted.map((pending) => {
      return pending.entry;
    });
    this.uploadService
      .addEntries(this.requestId(), entries)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((event) => {
        const pending = accepted[event.index];
        if (event.type === 'progress') {
          this.setProgress(pending.key, event.percent);
        } else if (event.type === 'uploaded') {
          this.added.emit(event.request);
          this.dequeue(pending.key);
        } else if (event.type === 'failed') {
          const reason = uploadErrorMessage(
            event.error,
            this.config(),
            'Could not upload this file.',
          );
          this.failed.set([...this.failed(), { filename: pending.name, reason }]);
          this.dequeue(pending.key);
        }
      });
  }

  private dequeue(key: number): void {
    this.queue.set(
      this.queue().filter((upload) => {
        return upload.key !== key;
      }),
    );
  }

  private setProgress(key: number, progress: number): void {
    this.queue.set(
      this.queue().map((upload) => {
        if (upload.key !== key) {
          return upload;
        }
        return { ...upload, progress };
      }),
    );
  }
}
