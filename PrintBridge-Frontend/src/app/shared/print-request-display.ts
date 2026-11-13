import { RequestStatus } from '../core/models/print-request.model';

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

/** Human-readable file size, e.g. "1.5 MB". */
export function formatBytes(bytes: number): string {
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
  return `${value.toFixed(1)} ${units[unit]}`;
}
