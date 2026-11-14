import { DatePipe, Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  numberAttribute,
  OnInit,
  signal,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { FileSaverService } from '../../../core/files/file-saver.service';
import { errorMessage } from '../../../core/http/api-error';
import {
  FailedUpload,
  FailedUploadsState,
  PrintRequest,
  RequestFile,
} from '../../../core/models/print-request.model';
import { formatDuration, formatGrams } from '../../../shared/model-info-display';
import { PriceTag } from '../../../shared/price-tag/price-tag';
import {
  allowsPayment,
  formatBytes,
  statusBadgeClass,
  statusLabel,
} from '../../../shared/print-request-display';
import { PrintRequestsService } from '../print-requests.service';
import { isValidQuantity, MAX_QUANTITY, MIN_QUANTITY } from '../upload-checks';
import { AddFiles } from './add-files/add-files';
import { ModelInfoCard } from './model-info-card/model-info-card';
import { ReviewPanel } from './review-panel/review-panel';

const FILE_TYPE_LABELS: Record<RequestFile['type'], string> = { STL: 'STL', THREE_MF: '3MF' };

/** How often the page reloads the request while one of its files is being sliced. */
export const SLICING_POLL_MS = 3000;

function isSlicing(request: PrintRequest): boolean {
  return request.files.some((file) => {
    const status = file.modelInfo.slicing.status;
    return status === 'QUEUED' || status === 'RUNNING';
  });
}

/** "1 file", "3 files" */
function counted(count: number, singular: string, plural: string): string {
  if (count === 1) {
    return `${count} ${singular}`;
  }
  return `${count} ${plural}`;
}

/** "~412 g" with a note on estimates and files left out; null while no weight is known. */
function filamentTotal(request: PrintRequest): { text: string; note: string | null } | null {
  const totals = request.totals;
  if (totals.grams === null) {
    return null;
  }
  const notes: string[] = [];
  let text = formatGrams(totals.grams);
  if (totals.weight !== 'EXACT') {
    text = `~${formatGrams(Math.round(totals.grams))}`;
    if (totals.weight === 'PARTLY_ESTIMATED') {
      notes.push('partly estimated');
    } else {
      notes.push('estimate');
    }
  }
  const unknown = request.files.filter((file) => {
    return file.modelInfo.totalGrams === null;
  }).length;
  if (unknown > 0) {
    notes.push(`${counted(unknown, 'file', 'files')} without details not counted`);
  }
  let note: string | null = null;
  if (notes.length > 0) {
    note = `(${notes.join('; ')})`;
  }
  return { text, note };
}

/**
 * "18 h 20 min", or "~12 h 5 min" with "(partial time)" when some files have no time yet (not
 * sliced); null while no file has one.
 */
function printTimeTotal(request: PrintRequest): { text: string; note: string | null } | null {
  const totals = request.totals;
  if (totals.printSeconds === null) {
    return null;
  }
  if (totals.printTimePartial) {
    return { text: `~${formatDuration(totals.printSeconds)}`, note: '(partial time)' };
  }
  return { text: formatDuration(totals.printSeconds), note: null };
}

/** One print request: details, its files (#23), delete and (admin) review. */
@Component({
  selector: 'app-request-detail-page',
  imports: [RouterLink, DatePipe, PriceTag, ReviewPanel, ModelInfoCard, AddFiles],
  templateUrl: './request-detail-page.html',
  styleUrl: './request-detail-page.css',
})
export class RequestDetailPage implements OnInit {
  private readonly requestsService = inject(PrintRequestsService);
  private readonly fileSaver = inject(FileSaverService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly location = inject(Location);
  protected readonly auth = inject(AuthService);
  private pollTimer: ReturnType<typeof setTimeout> | null = null;

  /** Route parameter `:id`. */
  readonly id = input.required({ transform: numberAttribute });

  protected readonly request = signal<PrintRequest | null>(null);
  protected readonly loading = signal(true);
  protected readonly notFound = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly downloadingId = signal<number | null>(null);
  protected readonly confirmingDelete = signal(false);
  protected readonly deleting = signal(false);
  /** The file a re-slice request is on its way for. */
  protected readonly reslicingId = signal<number | null>(null);
  /** Files the new request form couldn't add, handed over when it opened this page. */
  protected readonly failedUploads = signal<FailedUpload[]>([]);
  protected readonly downloadingAll = signal(false);
  protected readonly confirmingRemoveId = signal<number | null>(null);
  protected readonly removingId = signal<number | null>(null);
  protected readonly savingQuantityId = signal<number | null>(null);

  protected readonly minQuantity = MIN_QUANTITY;
  protected readonly maxQuantity = MAX_QUANTITY;

  /** Owners add and remove files while SUBMITTED; the admin always (the backend enforces the same). */
  protected readonly canChangeFiles = computed(() => {
    const request = this.request();
    return !!request && (this.auth.isAdmin() || request.status === 'SUBMITTED');
  });

  /** Owners change quantities while SUBMITTED; the admin until the request is final. */
  protected readonly canChangeQuantity = computed(() => {
    const request = this.request();
    if (!request) {
      return false;
    }
    if (this.auth.isAdmin()) {
      return request.nextStatuses.length > 0;
    }
    return request.status === 'SUBMITTED';
  });

  /** The last file can't be removed; that is deleting the request. */
  protected readonly canRemoveFiles = computed(() => {
    const request = this.request();
    return this.canChangeFiles() && !!request && request.files.length > 1;
  });

  /** "3 files · 7 copies", next to the Files heading. */
  protected readonly fileCounts = computed(() => {
    const request = this.request();
    if (!request) {
      return null;
    }
    const totals = request.totals;
    return `${counted(totals.files, 'file', 'files')} · ${counted(totals.copies, 'copy', 'copies')}`;
  });

  /** Filament and print time of the whole request: every file times its quantity. */
  protected readonly summary = computed(() => {
    const request = this.request();
    if (!request) {
      return null;
    }
    return { filament: filamentTotal(request), printTime: printTimeTotal(request) };
  });

  /** Owners may delete while SUBMITTED; the admin always (the backend enforces the same). */
  protected readonly canDelete = computed(() => {
    const request = this.request();
    return !!request && (this.auth.isAdmin() || request.status === 'SUBMITTED');
  });

  /**
   * The admin reviews non-final requests, and can still mark completed ones (un)paid. The
   * backend refuses anything else.
   */
  protected readonly canReview = computed(() => {
    const request = this.request();
    if (!request || !this.auth.isAdmin()) {
      return false;
    }
    return request.nextStatuses.length > 0 || allowsPayment(request.status);
  });

  /** The admin comes from the overview, users from their own list. */
  protected readonly back = computed(() => {
    if (this.auth.isAdmin()) {
      return { link: '/admin/requests', label: 'All requests' };
    }
    return { link: '/', label: 'My requests' };
  });

  protected readonly statusLabel = statusLabel;
  protected readonly statusBadgeClass = statusBadgeClass;
  protected readonly formatBytes = formatBytes;

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.stopPolling();
    });
    const state = this.location.getState() as FailedUploadsState | null;
    if (state?.failedUploads) {
      this.failedUploads.set(state.failedUploads);
    }
  }

  ngOnInit(): void {
    this.requestsService.get(this.id()).subscribe({
      next: (request) => {
        this.show(request);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        if (err instanceof HttpErrorResponse && err.status === 404) {
          this.notFound.set(true);
        } else {
          this.error.set(errorMessage(err, 'Could not load this request.'));
        }
        this.loading.set(false);
      },
    });
  }

  protected onReviewed(updated: PrintRequest): void {
    this.show(updated);
  }

  /** A file from "Add files" is in; the request comes back with it (and new totals). */
  protected onFileAdded(updated: PrintRequest): void {
    this.show(updated);
  }

  /** Admin: queue one file for server slicing again. */
  protected onReslice(file: RequestFile): void {
    this.reslicingId.set(file.id);
    this.error.set(null);
    this.requestsService.reslice(this.id(), file.id).subscribe({
      next: (updated) => {
        this.reslicingId.set(null);
        this.show(updated);
      },
      error: (err: unknown) => {
        this.reslicingId.set(null);
        this.error.set(errorMessage(err, 'Could not start slicing.'));
      },
    });
  }

  private show(request: PrintRequest): void {
    this.request.set(request);
    this.stopPolling();
    if (isSlicing(request)) {
      this.pollTimer = setTimeout(() => {
        this.refresh();
      }, SLICING_POLL_MS);
    }
  }

  /** Quiet reload while slicing; on an error the last state stays and it tries again. */
  private refresh(): void {
    this.requestsService.get(this.id()).subscribe({
      next: (request) => {
        this.show(request);
      },
      error: () => {
        const current = this.request();
        if (current) {
          this.show(current);
        }
      },
    });
  }

  private stopPolling(): void {
    if (this.pollTimer !== null) {
      clearTimeout(this.pollTimer);
      this.pollTimer = null;
    }
  }

  protected fileTypeLabel(file: RequestFile): string {
    return FILE_TYPE_LABELS[file.type];
  }

  protected download(file: RequestFile): void {
    this.downloadingId.set(file.id);
    this.error.set(null);
    this.requestsService.downloadFile(this.id(), file.id).subscribe({
      next: (blob) => {
        this.downloadingId.set(null);
        this.fileSaver.save(blob, file.originalFilename);
      },
      error: (err: unknown) => {
        this.downloadingId.set(null);
        this.error.set(errorMessage(err, 'Could not download the file.'));
      },
    });
  }

  protected downloadAll(): void {
    const request = this.request();
    if (!request) {
      return;
    }
    this.downloadingAll.set(true);
    this.error.set(null);
    this.requestsService.downloadAll(request.id).subscribe({
      next: (blob) => {
        this.downloadingAll.set(false);
        this.fileSaver.save(blob, `${request.name.replace(/[\\/]/g, '_')}.zip`);
      },
      error: (err: unknown) => {
        this.downloadingAll.set(false);
        this.error.set(errorMessage(err, 'Could not download the files.'));
      },
    });
  }

  /** Saves a new quantity once the field changes; an invalid one is put back. */
  protected changeQuantity(file: RequestFile, event: Event): void {
    const input = event.target as HTMLInputElement;
    const quantity = Number(input.value);
    if (quantity === file.quantity) {
      return;
    }
    if (!isValidQuantity(quantity)) {
      input.value = String(file.quantity);
      this.error.set(`The quantity must be a whole number from ${MIN_QUANTITY} to ${MAX_QUANTITY}`);
      return;
    }
    this.savingQuantityId.set(file.id);
    this.error.set(null);
    this.requestsService.changeQuantity(this.id(), file.id, quantity).subscribe({
      next: (updated) => {
        this.savingQuantityId.set(null);
        this.show(updated);
      },
      error: (err: unknown) => {
        this.savingQuantityId.set(null);
        input.value = String(file.quantity);
        this.error.set(errorMessage(err, 'Could not change the quantity.'));
      },
    });
  }

  protected askRemove(file: RequestFile): void {
    this.confirmingRemoveId.set(file.id);
  }

  protected cancelRemove(): void {
    this.confirmingRemoveId.set(null);
  }

  protected confirmRemove(file: RequestFile): void {
    this.removingId.set(file.id);
    this.error.set(null);
    this.requestsService.removeFile(this.id(), file.id).subscribe({
      next: (updated) => {
        this.removingId.set(null);
        this.confirmingRemoveId.set(null);
        this.show(updated);
      },
      error: (err: unknown) => {
        this.removingId.set(null);
        this.confirmingRemoveId.set(null);
        this.error.set(errorMessage(err, 'Could not remove the file.'));
      },
    });
  }

  protected askDelete(): void {
    this.confirmingDelete.set(true);
  }

  protected cancelDelete(): void {
    this.confirmingDelete.set(false);
  }

  protected confirmDelete(): void {
    this.deleting.set(true);
    this.error.set(null);
    this.requestsService.delete(this.id()).subscribe({
      next: () => {
        this.deleting.set(false);
        void this.router.navigate(['/']);
      },
      error: (err: unknown) => {
        this.deleting.set(false);
        this.confirmingDelete.set(false);
        this.error.set(errorMessage(err, 'Could not delete this request.'));
      },
    });
  }
}
