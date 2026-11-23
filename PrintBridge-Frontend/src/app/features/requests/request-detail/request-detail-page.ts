import { DatePipe, Location, NgTemplateOutlet } from '@angular/common';
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
  viewChild,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AuthService } from '../../../core/auth/auth.service';
import { FileSaverService } from '../../../core/files/file-saver.service';
import { errorMessage } from '../../../core/http/api-error';
import {
  FailedUpload,
  FailedUploadsState,
  PrintRequest,
  RequestFile,
  UploadedFile,
} from '../../../core/models/print-request.model';
import { formatDuration, formatGrams } from '../../../shared/format/model-info-display';
import { PriceTag } from '../../../shared/price-tag/price-tag';
import {
  allowsPayment,
  formatBytes,
  itemProgressText,
  linkLabel,
} from '../../../shared/format/print-request-display';
import { HasPendingUploads } from '../pending-uploads.guard';
import { PrintRequestsService } from '../print-requests.service';
import { isValidQuantity, MAX_QUANTITY, MIN_QUANTITY } from '../upload-checks';
import { AddFiles } from './add-files/add-files';
import { DetailsEditor } from './details-editor/details-editor';
import { ItemStatusControl } from './item-status/item-status';
import { RequestStatusControl } from './request-status/request-status';
import { LinkCard } from './link-card/link-card';
import { ModelInfoCard } from './model-info-card/model-info-card';
import { ResliceDialog } from './reslice-dialog/reslice-dialog';
import { ReviewPanel } from './review-panel/review-panel';
import { FilamentChoiceInput } from '../../../core/models/spoolman.model';
import { SpoolmanFilamentsService } from '../spoolman-filaments.service';

const FILE_TYPE_LABELS: Record<UploadedFile['type'], string> = { STL: 'STL', THREE_MF: '3MF' };

/** How often the page reloads the request while one of its files is being sliced. */
export const SLICING_POLL_MS = 3000;

function isSlicing(request: PrintRequest): boolean {
  return uploadedFiles(request).some((file) => {
    const status = file.modelInfo.slicing.status;
    return status === 'QUEUED' || status === 'RUNNING';
  });
}

/** The uploaded files of a request, without its links (#59). */
function uploadedFiles(request: PrintRequest): UploadedFile[] {
  return request.files.filter((file): file is UploadedFile => {
    return file.kind === 'FILE';
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
  // Links are already marked by the weight (partly estimated), like a plate that wasn't sliced;
  // declined files (#61) aren't counted at all.
  const unknown = uploadedFiles(request).filter((file) => {
    return file.status !== 'DECLINED' && file.modelInfo.totalGrams === null;
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
  imports: [
    RouterLink,
    DatePipe,
    NgTemplateOutlet,
    PriceTag,
    ReviewPanel,
    ModelInfoCard,
    LinkCard,
    ItemStatusControl,
    RequestStatusControl,
    AddFiles,
    DetailsEditor,
  ],
  templateUrl: './request-detail-page.html',
  styleUrl: './request-detail-page.css',
  // One filament list per visit, shared by every colour circle on the page (#11).
  providers: [SpoolmanFilamentsService],
})
export class RequestDetailPage implements OnInit, HasPendingUploads {
  private readonly requestsService = inject(PrintRequestsService);
  private readonly filamentsService = inject(SpoolmanFilamentsService);
  private readonly fileSaver = inject(FileSaverService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly location = inject(Location);
  private readonly modalService = inject(NgbModal);
  protected readonly auth = inject(AuthService);
  private pollTimer: ReturnType<typeof setTimeout> | null = null;

  /** Only there while files may be added. */
  private readonly addFiles = viewChild(AddFiles);

  /** Route parameter `:id`. */
  readonly id = input.required({ transform: numberAttribute });

  protected readonly request = signal<PrintRequest | null>(null);
  protected readonly loading = signal(true);
  protected readonly notFound = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly downloadingId = signal<number | null>(null);
  protected readonly confirmingDelete = signal(false);
  protected readonly deleting = signal(false);
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

  /** Name and description (#84): like quantities, the owner while SUBMITTED, the admin until final. */
  protected readonly canEditDetails = computed(() => {
    return this.canChangeQuantity();
  });

  /** The name and description are being edited (#84). */
  protected readonly editingDetails = signal(false);

  /** The last file or link can't be removed; that is deleting the request. */
  protected readonly canRemoveFiles = computed(() => {
    const request = this.request();
    return this.canChangeFiles() && !!request && request.files.length > 1;
  });

  /**
   * "3 files · 2 links · 9 copies · 1 declined · 2/4 completed" next to the Files heading; parts
   * with nothing to say are left out (#59, #61).
   */
  protected readonly fileCounts = computed(() => {
    const request = this.request();
    if (!request) {
      return null;
    }
    const totals = request.totals;
    const parts: string[] = [];
    if (totals.files > 0 || totals.links === 0) {
      parts.push(counted(totals.files, 'file', 'files'));
    }
    if (totals.links > 0) {
      parts.push(counted(totals.links, 'link', 'links'));
    }
    parts.push(counted(totals.copies, 'copy', 'copies'));
    if (totals.declined > 0) {
      parts.push(`${totals.declined} declined`);
    }
    const progress = itemProgressText(
      request.status,
      totals.completed,
      totals.files + totals.links - totals.declined,
    );
    if (progress) {
      parts.push(progress);
    }
    return parts.join(' · ');
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

  /** Whether "Add files" is uploading right now; leaving the page then asks first. */
  hasPendingUploads(): boolean {
    return this.addFiles()?.busy() ?? false;
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

  protected startEditingDetails(): void {
    this.editingDetails.set(true);
  }

  protected onDetailsSaved(updated: PrintRequest): void {
    this.editingDetails.set(false);
    this.show(updated);
  }

  protected stopEditingDetails(): void {
    this.editingDetails.set(false);
  }

  /** A file from "Add files" is in; the request comes back with it (and new totals). */
  protected onFileAdded(updated: PrintRequest): void {
    this.show(updated);
  }

  /** Admin: an item's status changed (#61); the request comes back with new totals. */
  protected onItemChanged(updated: PrintRequest): void {
    this.show(updated);
  }

  /**
   * Admin: "Re-slice with…" (#21) opens the dialog for one file; once it queued the file, the
   * page shows the request it answered with (and polls while it slices).
   */
  protected onResliceWith(file: UploadedFile): void {
    const modal = this.modalService.open(ResliceDialog);
    const dialog = modal.componentInstance as ResliceDialog;
    dialog.requestId = this.id();
    dialog.fileId = file.id;
    dialog.filename = file.originalFilename;
    modal.result.then(
      (updated: PrintRequest) => {
        this.show(updated);
      },
      () => {
        // Cancelled: nothing changes.
      },
    );
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

  protected fileTypeLabel(file: UploadedFile): string {
    return FILE_TYPE_LABELS[file.type];
  }

  /** The file name, or the link's name or host. */
  protected entryName(file: RequestFile): string {
    if (file.kind === 'LINK') {
      return linkLabel(file.url, file.linkName);
    }
    return file.originalFilename;
  }

  protected download(file: UploadedFile): void {
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

  /**
   * Saves the Spoolman filament chosen for a colour (#11). A refused choice (e.g. the filament was
   * deleted in Spoolman) reloads the list, so the dropdown shows what is there now.
   */
  protected chooseFilament(file: RequestFile, choice: FilamentChoiceInput): void {
    this.error.set(null);
    this.requestsService.chooseFilament(this.id(), file.id, choice).subscribe({
      next: (updated) => {
        this.show(updated);
      },
      error: (err: unknown) => {
        this.error.set(errorMessage(err, 'Could not choose the filament.'));
        this.filamentsService.load(true);
      },
    });
  }

  /** Removes a chosen filament: the colour goes back to the file's own. */
  protected resetFilament(file: RequestFile, choiceId: number): void {
    this.error.set(null);
    this.requestsService.removeFilamentChoice(this.id(), file.id, choiceId).subscribe({
      next: (updated) => {
        this.show(updated);
      },
      error: (err: unknown) => {
        this.error.set(errorMessage(err, 'Could not remove the filament.'));
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
        this.error.set(errorMessage(err, 'Could not remove it.'));
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
