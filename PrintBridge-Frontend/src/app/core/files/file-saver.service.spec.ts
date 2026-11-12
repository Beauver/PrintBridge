import { TestBed } from '@angular/core/testing';
import { FileSaverService } from './file-saver.service';

describe('FileSaverService', () => {
  it('clicks a temporary download link and revokes the object URL', () => {
    vi.useFakeTimers();
    const createUrl = vi.fn(() => {
      return 'blob:url';
    });
    const revokeUrl = vi.fn();
    vi.stubGlobal('URL', { ...URL, createObjectURL: createUrl, revokeObjectURL: revokeUrl });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {
      // don't navigate in tests
    });

    const blob = new Blob(['x']);
    TestBed.inject(FileSaverService).save(blob, 'cube.stl');

    expect(createUrl).toHaveBeenCalledWith(blob);
    const link = click.mock.contexts[0] as HTMLAnchorElement;
    expect(link.download).toBe('cube.stl');
    expect(link.href).toBe('blob:url');
    expect(revokeUrl).not.toHaveBeenCalled();
    vi.runAllTimers();
    expect(revokeUrl).toHaveBeenCalledWith('blob:url');

    click.mockRestore();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });
});
