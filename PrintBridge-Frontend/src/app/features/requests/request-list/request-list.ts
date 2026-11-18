import { DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PrintRequestSummary } from '../../../core/models/print-request.model';
import { PriceTag } from '../../../shared/price-tag/price-tag';
import {
  itemProgressText,
  statusBadgeClass,
  statusLabel,
} from '../../../shared/format/print-request-display';

/**
 * Presentational list of print requests: a table on wider screens, cards on phones.
 * Used by "My requests" and the admin overview (with `showOwner`).
 */
@Component({
  selector: 'app-request-list',
  imports: [RouterLink, DatePipe, PriceTag],
  templateUrl: './request-list.html',
})
export class RequestList {
  readonly requests = input.required<PrintRequestSummary[]>();
  /** Adds an owner column; for lists that span several users (admin overview). */
  readonly showOwner = input(false);

  protected readonly statusLabel = statusLabel;

  /** "2/3 completed" next to the status badge (#61). */
  protected progress(request: PrintRequestSummary): string | null {
    return itemProgressText(request.status, request.itemsCompleted, request.itemsActive);
  }
  protected readonly statusBadgeClass = statusBadgeClass;
}
