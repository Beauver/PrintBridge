import { ItemStatus, RequestStatus } from '../../core/models/print-request.model';
import { formatDecimal, getBrowserLocale } from './locale';

const STATUS_LABELS: Record<RequestStatus, string> = {
  SUBMITTED: 'Submitted',
  IN_REVIEW: 'In review',
  ACCEPTED: 'Accepted',
  PRINTING: 'Printing',
  DECLINED: 'Declined',
  COMPLETED: 'Completed',
};

const STATUS_BADGES: Record<RequestStatus, string> = {
  SUBMITTED: 'text-bg-secondary',
  IN_REVIEW: 'text-bg-info',
  ACCEPTED: 'text-bg-primary',
  PRINTING: 'text-bg-warning',
  DECLINED: 'text-bg-danger',
  COMPLETED: 'text-bg-success',
};

const ITEM_STATUS_LABELS: Record<ItemStatus, string> = {
  PENDING: 'Pending',
  ACCEPTED: 'Accepted',
  DECLINED: 'Declined',
  PRINTING: 'Printing',
  COMPLETED: 'Completed',
};

/** The same colours as the request statuses (#61). */
const ITEM_STATUS_BADGES: Record<ItemStatus, string> = {
  PENDING: 'text-bg-secondary',
  ACCEPTED: 'text-bg-primary',
  DECLINED: 'text-bg-danger',
  PRINTING: 'text-bg-warning',
  COMPLETED: 'text-bg-success',
};

/** Request statuses from which item progress is shown. */
const PROGRESS_STATUSES: RequestStatus[] = ['ACCEPTED', 'PRINTING', 'COMPLETED'];

/** Statuses after which nothing on a request can change. */
const FINAL_STATUSES: RequestStatus[] = ['DECLINED', 'COMPLETED'];

export function isFinalStatus(status: RequestStatus): boolean {
  return FINAL_STATUSES.includes(status);
}

/** Whether the admin may mark a request (un)paid in this status; mirrors the backend. */
export function allowsPayment(status: RequestStatus): boolean {
  return status === 'ACCEPTED' || status === 'PRINTING' || status === 'COMPLETED';
}

export function statusLabel(status: RequestStatus): string {
  return STATUS_LABELS[status];
}

/** Bootstrap badge classes, one colour per status. */
export function statusBadgeClass(status: RequestStatus): string {
  return STATUS_BADGES[status];
}

/** Human-readable file size in the browser locale (#86), e.g. "1.5 MB". */
export function formatBytes(bytes: number, locale = getBrowserLocale()): string {
  const units = ['B', 'KB', 'MB', 'GB'];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  if (unit === 0) {
    return `${value} ${units[unit]}`;
  }
  return `${formatDecimal(value, 1, 1, locale)} ${units[unit]}`;
}

/** A link's name (#59), or else the host of its URL ("www.patreon.com"), or else the URL itself. */
export function linkLabel(url: string, name: string | null): string {
  if (name && name.trim()) {
    return name.trim();
  }
  try {
    const host = new URL(url).hostname;
    if (host) {
      return host;
    }
  } catch {
    // Not parseable; show it as it is.
  }
  return url;
}

export function itemStatusLabel(status: ItemStatus): string {
  return ITEM_STATUS_LABELS[status];
}

export function itemStatusBadgeClass(status: ItemStatus): string {
  return ITEM_STATUS_BADGES[status];
}

/**
 * "2/3 completed" (#61): completed items of those that aren't declined. Only from Accepted
 * onwards and when there is more than one such item; null otherwise.
 */
export function itemProgressText(
  status: RequestStatus,
  completed: number,
  active: number,
): string | null {
  if (!PROGRESS_STATUSES.includes(status) || active <= 1) {
    return null;
  }
  return `${completed}/${active} completed`;
}
