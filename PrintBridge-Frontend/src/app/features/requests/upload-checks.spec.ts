import { HttpErrorResponse } from '@angular/common/http';
import { UploadConfig } from '../../core/models/print-request.model';
import {
  checkFile,
  extensionOf,
  FALLBACK_CONFIG,
  isValidQuantity,
  limitsText,
  uploadErrorMessage,
} from './upload-checks';

describe('upload checks', () => {
  const CONFIG: UploadConfig = { maxSizeBytes: 1024, maxFiles: 10, allowedExtensions: ['stl'] };

  function file(size: number, name = 'a.stl'): File {
    return new File([new Uint8Array(size)], name);
  }

  it('reads the extension', () => {
    expect(extensionOf('Box.3MF')).toBe('3mf');
    expect(extensionOf('noextension')).toBe('');
  });

  it('checks type, emptiness and size', () => {
    expect(checkFile(file(10), CONFIG)).toBeNull();
    expect(checkFile(file(10, 'a.3mf'), CONFIG)).toBe('Only .stl files are allowed');
    expect(checkFile(file(0), CONFIG)).toBe('The file is empty');
    expect(checkFile(file(2048), CONFIG)).toBe('The file is larger than 1.0 KB');
    expect(checkFile(file(2048), FALLBACK_CONFIG)).toBeNull();
  });

  it('accepts whole quantities from 1 to 100', () => {
    expect(isValidQuantity(1)).toBe(true);
    expect(isValidQuantity(100)).toBe(true);
    expect(isValidQuantity(0)).toBe(false);
    expect(isValidQuantity(101)).toBe(false);
    expect(isValidQuantity(2.5)).toBe(false);
    expect(isValidQuantity(Number.NaN)).toBe(false);
  });

  it('describes the limits it knows', () => {
    expect(limitsText(3, CONFIG)).toBe('3 of 10 files · max 1.0 KB each');
    expect(limitsText(3, { ...CONFIG, maxSizeBytes: 0 })).toBe('3 of 10 files');
    expect(limitsText(3, FALLBACK_CONFIG)).toBe('');
  });

  it('explains upload errors', () => {
    const tooLarge = new HttpErrorResponse({ status: 413 });
    expect(uploadErrorMessage(tooLarge, CONFIG, 'Failed')).toBe('The file is larger than 1.0 KB');
    expect(uploadErrorMessage(tooLarge, FALLBACK_CONFIG, 'Failed')).toBe('The file is too large');
    const refused = new HttpErrorResponse({ status: 400, error: { message: 'Not an STL' } });
    expect(uploadErrorMessage(refused, CONFIG, 'Failed')).toBe('Not an STL');
  });
});
