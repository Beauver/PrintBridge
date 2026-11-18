import {
  allowsPayment,
  formatBytes,
  isFinalStatus,
  itemProgressText,
  itemStatusBadgeClass,
  itemStatusLabel,
  linkLabel,
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

  it('labels a link by its name, else its host, else the URL', () => {
    expect(linkLabel('https://www.patreon.com/posts/1', ' Dragon ')).toBe('Dragon');
    expect(linkLabel('https://www.patreon.com/posts/1', null)).toBe('www.patreon.com');
    expect(linkLabel('https://www.patreon.com/posts/1', '  ')).toBe('www.patreon.com');
    expect(linkLabel('not a url', null)).toBe('not a url');
  });

  it('labels and colours item statuses like request statuses (#61)', () => {
    expect(itemStatusLabel('PENDING')).toBe('Pending');
    expect(itemStatusLabel('COMPLETED')).toBe('Completed');
    expect(itemStatusBadgeClass('ACCEPTED')).toBe(statusBadgeClass('ACCEPTED'));
    expect(itemStatusBadgeClass('PRINTING')).toBe(statusBadgeClass('PRINTING'));
    expect(itemStatusBadgeClass('DECLINED')).toBe(statusBadgeClass('DECLINED'));
    expect(itemStatusBadgeClass('PENDING')).toBe('text-bg-secondary');
  });

  it('shows item progress only from Accepted on and with more than one item', () => {
    expect(itemProgressText('ACCEPTED', 0, 3)).toBe('0/3 completed');
    expect(itemProgressText('PRINTING', 1, 2)).toBe('1/2 completed');
    expect(itemProgressText('COMPLETED', 4, 4)).toBe('4/4 completed');
    expect(itemProgressText('SUBMITTED', 0, 3)).toBeNull();
    expect(itemProgressText('DECLINED', 0, 3)).toBeNull();
    expect(itemProgressText('ACCEPTED', 1, 1)).toBeNull();
  });
});
