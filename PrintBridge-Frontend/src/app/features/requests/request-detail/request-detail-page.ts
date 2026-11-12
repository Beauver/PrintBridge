import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, numberAttribute, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { FileSaverService } from '../../../core/files/file-saver.service';
import { errorMessage } from '../../../core/http/api-error';
import { PrintRequest, RequestFile } from '../../../core/models';
import { formatBytes, statusBadgeClass, statusLabel } from '../../../shared/print-request-display';
import { PrintRequestsService } from '../print-requests.service';

const FILE_TYPE_LABELS: Record<RequestFile['type'], string> = { STL: 'STL', THREE_MF: '3MF' };

/** One print request: details, file download and delete. */
// TODO: #5 admin review actions (status, price, decline reason).
@Component({
  selector: 'app-request-detail-page',
  imports: [RouterLink, DatePipe, CurrencyPipe],
  templateUrl: './request-detail-page.html',
  styleUrl: './request-detail-page.css',
})
export class RequestDetailPage implements OnInit {
  private readonly requestsService = inject(PrintRequestsService);
  private readonly fileSaver = inject(FileSaverService);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  /** Route parameter `:id`. */
  readonly id = input.required({ transform: numberAttribute });

  protected readonly request = signal<PrintRequest | null>(null);
  protected readonly loading = signal(true);
  protected readonly notFound = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly downloadingId = signal<number | null>(null);
  protected readonly confirmingDelete = signal(false);
  protected readonly deleting = signal(false);

  /** Owners may delete while SUBMITTED; the admin always (the backend enforces the same). */
  protected readonly canDelete = computed(() => {
    const request = this.request();
    return !!request && (this.auth.isAdmin() || request.status === 'SUBMITTED');
  });

  protected readonly statusLabel = statusLabel;
  protected readonly statusBadgeClass = statusBadgeClass;
  protected readonly formatBytes = formatBytes;

  ngOnInit(): void {
    this.requestsService.get(this.id()).subscribe({
      next: (request) => {
        this.request.set(request);
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
