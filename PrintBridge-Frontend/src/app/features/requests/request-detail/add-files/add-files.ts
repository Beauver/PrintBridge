import { Component, computed, inject, input, OnInit, output, signal } from '@angular/core';
import {
  FailedUpload,
  PrintRequest,
  UploadConfig,
} from '../../../../core/models/print-request.model';
import { PrintRequestsService } from '../../print-requests.service';
import {
  checkFile,
  dotted,
  FALLBACK_CONFIG,
  limitsText,
  MIN_QUANTITY,
  uploadErrorMessage,
} from '../../upload-checks';

/** A file on its way up. */
export interface PendingUpload {
  key: number;
  file: File;
  progress: number;
}

/**
 * The "Add files" button of a request (#23): checks the chosen files against the limits, then
 * uploads them one at a time with one copy each. Every upload that succeeds hands the updated
 * request to the page; the ones that fail are listed with the reason.
 */
@Component({
  selector: 'app-add-files',
  templateUrl: './add-files.html',
})
export class AddFiles implements OnInit {
  private readonly requestsService = inject(PrintRequestsService);
  private nextKey = 1;

  readonly requestId = input.required<number>();
  /** Files the request holds now. */
  readonly fileCount = input.required<number>();
  readonly added = output<PrintRequest>();

  protected readonly config = signal<UploadConfig>(FALLBACK_CONFIG);
  /** Waiting and uploading, in order; the first one is going up. */
  protected readonly queue = signal<PendingUpload[]>([]);
  protected readonly failed = signal<FailedUpload[]>([]);

  protected readonly busy = computed(() => {
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
        failed.push({
          filename: file.name,
          reason: `A request can hold at most ${this.config().maxFiles} files`,
        });
      } else {
        accepted.push({ key: this.nextKey, file, progress: 0 });
        this.nextKey++;
        room--;
      }
    }
    this.failed.set(failed);
    this.queue.set(accepted);
    this.uploadNext();
  }

  protected dismissFailed(): void {
    this.failed.set([]);
  }

  /** How many more files fit; unlimited until the config is known (the backend checks anyway). */
  private room(): number {
    const maxFiles = this.config().maxFiles;
    if (maxFiles <= 0) {
      return Number.MAX_SAFE_INTEGER;
    }
    return maxFiles - this.fileCount();
  }

  private uploadNext(): void {
    const [next] = this.queue();
    if (!next) {
      return;
    }
    this.requestsService.addFile(this.requestId(), next.file, MIN_QUANTITY).subscribe({
      next: (event) => {
        if (event.type === 'progress') {
          this.setProgress(next.key, event.percent);
        } else {
          this.added.emit(event.request);
          this.dequeue(next.key);
        }
      },
      error: (err: unknown) => {
        const reason = uploadErrorMessage(err, this.config(), 'Could not upload this file.');
        this.failed.set([...this.failed(), { filename: next.file.name, reason }]);
        this.dequeue(next.key);
      },
    });
  }

  private dequeue(key: number): void {
    this.queue.set(
      this.queue().filter((upload) => {
        return upload.key !== key;
      }),
    );
    this.uploadNext();
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
