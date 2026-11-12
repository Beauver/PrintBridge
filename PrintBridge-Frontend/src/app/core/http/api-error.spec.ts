import { HttpErrorResponse } from '@angular/common/http';
import { errorMessage } from './api-error';

describe('errorMessage', () => {
  it('uses the backend message', () => {
    const error = new HttpErrorResponse({
      status: 409,
      error: { message: 'Username is already taken' },
    });
    expect(errorMessage(error)).toBe('Username is already taken');
  });

  it('uses the first validation violation', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: { violations: [{ message: 'Email is not valid' }] },
    });
    expect(errorMessage(error)).toBe('Email is not valid');
  });

  it('reports an unreachable server', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 0 }))).toBe('Cannot reach the server.');
  });

  it('falls back for unknown errors', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 500, error: 'boom' }), 'Fallback')).toBe(
      'Fallback',
    );
    expect(errorMessage(new Error('x'), 'Fallback')).toBe('Fallback');
  });
});
