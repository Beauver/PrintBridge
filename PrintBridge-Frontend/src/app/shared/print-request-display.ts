import { RequestStatus } from '../core/models';

const STATUS_LABELS: Record<RequestStatus, string> = {
  SUBMITTED: 'Submitted',
  IN_REVIEW: 'In review',
  ACCEPTED: 'Accepted',
  DECLINED: 'Declined',
  COMPLETED: 'Completed',
};

const STATUS_BADGES: Record<RequestStatus, string> = {
  SUBMITTED: 'text-bg-secondary',
  IN_REVIEW: 'text-bg-info',
  ACCEPTED: 'text-bg-primary',
  DECLINED: 'text-bg-danger',
  COMPLETED: 'text-bg-success',
};

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
  return `${unit === 0 ? value : value.toFixed(1)} ${units[unit]}`;
}
