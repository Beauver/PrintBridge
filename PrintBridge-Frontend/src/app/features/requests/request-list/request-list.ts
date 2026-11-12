import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PrintRequestSummary } from '../../../core/models';
import { statusBadgeClass, statusLabel } from '../../../shared/print-request-display';

/**
 * Presentational list of print requests: a table on wider screens, cards on phones.
 * Reused by the admin overview in #5 (with `showOwner`).
 */
@Component({
  selector: 'app-request-list',
  imports: [RouterLink, DatePipe, CurrencyPipe],
  templateUrl: './request-list.html',
})
export class RequestList {
  readonly requests = input.required<PrintRequestSummary[]>();
  /** Adds an owner column; for lists that span several users (admin overview). */
  readonly showOwner = input(false);

  protected readonly statusLabel = statusLabel;
  protected readonly statusBadgeClass = statusBadgeClass;
}
