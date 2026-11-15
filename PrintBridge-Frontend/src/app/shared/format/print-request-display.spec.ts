import {
  allowsPayment,
  formatBytes,
  isFinalStatus,
  statusBadgeClass,
  statusLabel,
} from './print-request-display';

describe('print request display helpers', () => {
  it('labels and colours every status', () => {
    expect(statusLabel('IN_REVIEW')).toBe('In review');
    expect(statusBadgeClass('SUBMITTED')).toBe('text-bg-secondary');
    expect(statusBadgeClass('DECLINED')).toBe('text-bg-danger');
    expect(statusBadgeClass('COMPLETED')).toBe('text-bg-success');
    expect(statusLabel('PRINTING')).toBe('Printing');
    expect(statusBadgeClass('PRINTING')).toBe('text-bg-warning');
  });

  it('knows the final statuses', () => {
    expect(isFinalStatus('DECLINED')).toBe(true);
    expect(isFinalStatus('COMPLETED')).toBe(true);
    expect(isFinalStatus('ACCEPTED')).toBe(false);
    expect(isFinalStatus('PRINTING')).toBe(false);
  });

  it('allows payment from accepted on', () => {
    expect(allowsPayment('ACCEPTED')).toBe(true);
    expect(allowsPayment('PRINTING')).toBe(true);
    expect(allowsPayment('COMPLETED')).toBe(true);
    expect(allowsPayment('IN_REVIEW')).toBe(false);
    expect(allowsPayment('DECLINED')).toBe(false);
  });

  it('formats byte sizes', () => {
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(1536)).toBe('1.5 KB');
    expect(formatBytes(100 * 1024 * 1024)).toBe('100.0 MB');
    expect(formatBytes(3 * 1024 ** 4)).toBe('3072.0 GB');
  });
});
