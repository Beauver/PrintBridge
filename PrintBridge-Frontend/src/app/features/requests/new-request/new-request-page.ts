import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { PreviewMesh } from '../../../core/models/model-preview.model';
import {
  FailedUpload,
  FailedUploadsState,
  UploadConfig,
} from '../../../core/models/print-request.model';
import { fieldError } from '../../../shared/account-form';
import { ModelPreview } from '../../../shared/model-viewer/model-preview';
import { formatBytes } from '../../../shared/print-request-display';
import { ModelPreviewService } from '../model-preview.service';
import { PrintRequestsService } from '../print-requests.service';
import {
  checkFile,
  dotted,
  FALLBACK_CONFIG,
  isValidQuantity,
  limitsText,
  MAX_QUANTITY,
  MIN_QUANTITY,
  uploadErrorMessage,
} from '../upload-checks';

export type EntryStatus = 'waiting' | 'uploading' | 'done' | 'failed';

/** A chosen file, its number of copies and how its upload goes. */
export interface FileEntry {
  /** Local key; the same file may be chosen twice. */
  key: number;
  file: File;
  quantity: number;
  /** Why the file can't be uploaded; checked when it is chosen. */
  error: string | null;
  /** Loads the preview before uploading (#13); null for a file that can't be uploaded. */
  loader: (() => Observable<PreviewMesh>) | null;
  status: EntryStatus;
  progress: number;
}

/**
 * Form to submit a new print request with one or more model files (#23). The request is created
 * with the first file; the others follow one by one, so one failing doesn't lose the rest.
 */
@Component({
  selector: 'app-new-request-page',
  imports: [ReactiveFormsModule, RouterLink, ModelPreview],
  templateUrl: './new-request-page.html',
  styleUrl: './new-request-page.css',
})
export class NewRequestPage implements OnInit {
  private readonly requestsService = inject(PrintRequestsService);
  private readonly previewService = inject(ModelPreviewService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private nextKey = 1;

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    description: ['', Validators.maxLength(2000)],
  });

  protected readonly config = signal<UploadConfig>(FALLBACK_CONFIG);
  protected readonly entries = signal<FileEntry[]>([]);
  /** Set once the user tried to submit, so "file required" isn't shown up front. */
  protected readonly submitted = signal(false);
  protected readonly uploading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly minQuantity = MIN_QUANTITY;
  protected readonly maxQuantity = MAX_QUANTITY;

  protected readonly accept = computed(() => {
    return dotted(this.config().allowedExtensions).join(',');
  });

  /** "3 of 10 files · max 100 MB each" */
  protected readonly limits = computed(() => {
    return limitsText(this.entries().length, this.config());
  });

  /** About the list as a whole: no file, or more than the limit. */
  protected readonly filesError = computed(() => {
    const count = this.entries().length;
    if (count === 0) {
      if (this.submitted()) {
        return 'Add at least one model file';
      }
      return null;
    }
    const maxFiles = this.config().maxFiles;
    if (maxFiles > 0 && count > maxFiles) {
      return `A request can hold at most ${maxFiles} files`;
    }
    return null;
  });

  protected readonly hasFileErrors = computed(() => {
    if (this.filesError()) {
      return true;
    }
    return this.entries().some((entry) => {
      return entry.error !== null || !isValidQuantity(entry.quantity);
    });
  });

  /** "Uploading 2 of 3…" while the files go up. */
  protected readonly uploadingText = computed(() => {
    const entries = this.entries();
    const current = entries.findIndex((entry) => {
      return entry.status === 'uploading';
    });
    if (entries.length <= 1 || current < 0) {
      return 'Uploading…';
    }
    return `Uploading ${current + 1} of ${entries.length}…`;
  });

  protected readonly fieldError = fieldError;
  protected readonly formatBytes = formatBytes;
  protected readonly isValidQuantity = isValidQuantity;

  ngOnInit(): void {
    this.requestsService.uploadConfig().subscribe({
      next: (config) => {
        this.config.set(config);
        this.recheck();
      },
      error: () => {
        // Keep the fallback; the server still enforces its limits.
      },
    });
  }

  /** Adds the chosen files to the list; choosing again adds more. */
  protected onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const chosen = Array.from(input.files ?? []);
    const added = chosen.map((file) => {
      return this.entryFor(file);
    });
    this.entries.set([...this.entries(), ...added]);
    // So choosing the same file again still fires a change.
    input.value = '';
  }

  protected removeEntry(key: number): void {
    this.entries.set(
      this.entries().filter((entry) => {
        return entry.key !== key;
      }),
    );
  }

  protected setQuantity(key: number, event: Event): void {
    const quantity = Number((event.target as HTMLInputElement).value);
    this.update(key, { quantity });
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    this.submitted.set(true);
    if (this.form.invalid || this.hasFileErrors()) {
      return;
    }
    const [first] = this.entries();
    const { name, description } = this.form.getRawValue();
    this.uploading.set(true);
    this.error.set(null);
    this.update(first.key, { status: 'uploading', progress: 0 });
    this.requestsService
      .create({ name, description, file: first.file, quantity: first.quantity })
      .subscribe({
        next: (event) => {
          if (event.type === 'progress') {
            this.update(first.key, { progress: event.percent });
          } else {
            this.update(first.key, { status: 'done', progress: 100 });
            this.uploadRest(event.request.id, 1, []);
          }
        },
        error: (err: unknown) => {
          this.uploading.set(false);
          this.update(first.key, { status: 'waiting', progress: 0 });
          this.error.set(uploadErrorMessage(err, this.config(), 'Could not submit your request.'));
        },
      });
  }

  /** Adds the remaining files one at a time; a failure is remembered and the next one goes on. */
  private uploadRest(requestId: number, index: number, failed: FailedUpload[]): void {
    const entries = this.entries();
    if (index >= entries.length) {
      this.finish(requestId, failed);
      return;
    }
    const entry = entries[index];
    this.update(entry.key, { status: 'uploading', progress: 0 });
    this.requestsService.addFile(requestId, entry.file, entry.quantity).subscribe({
      next: (event) => {
        if (event.type === 'progress') {
          this.update(entry.key, { progress: event.percent });
        } else {
          this.update(entry.key, { status: 'done', progress: 100 });
          this.uploadRest(requestId, index + 1, failed);
        }
      },
      error: (err: unknown) => {
        // A full red bar, so "Failed" is readable.
        this.update(entry.key, { status: 'failed', progress: 100 });
        const reason = uploadErrorMessage(err, this.config(), 'Could not upload this file.');
        this.uploadRest(requestId, index + 1, [...failed, { filename: entry.file.name, reason }]);
      },
    });
  }

  /** Opens the new request; its page lists the files that didn't make it. */
  private finish(requestId: number, failed: FailedUpload[]): void {
    this.uploading.set(false);
    if (failed.length === 0) {
      void this.router.navigate(['/requests', requestId]);
      return;
    }
    const state: FailedUploadsState = { failedUploads: failed };
    void this.router.navigate(['/requests', requestId], { state });
  }

  private entryFor(file: File): FileEntry {
    const entry: FileEntry = {
      key: this.nextKey,
      file,
      quantity: MIN_QUANTITY,
      error: null,
      loader: null,
      status: 'waiting',
      progress: 0,
    };
    this.nextKey++;
    return this.checked(entry);
  }

  /** Checks the file against the current limits and sets its error and preview. */
  private checked(entry: FileEntry): FileEntry {
    const error = checkFile(entry.file, this.config());
    let loader: (() => Observable<PreviewMesh>) | null = null;
    if (!error) {
      // Kept on the entry so the preview isn't reset on every change.
      loader = entry.loader;
      if (!loader) {
        loader = () => {
          return this.previewService.uploadMesh(entry.file);
        };
      }
    }
    return { ...entry, error, loader };
  }

  /** The limits arrived after files were chosen: check them again. */
  private recheck(): void {
    this.entries.set(
      this.entries().map((entry) => {
        return this.checked(entry);
      }),
    );
  }

  private update(key: number, changes: Partial<FileEntry>): void {
    this.entries.set(
      this.entries().map((entry) => {
        if (entry.key !== key) {
          return entry;
        }
        return { ...entry, ...changes };
      }),
    );
  }
}
