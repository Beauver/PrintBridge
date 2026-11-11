import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-bs-theme');
  });

  it('defaults to dark', () => {
    const service = TestBed.inject(ThemeService);
    TestBed.tick();
    expect(service.theme()).toBe('dark');
    expect(document.documentElement.getAttribute('data-bs-theme')).toBe('dark');
  });

  it('toggles and remembers the choice', () => {
    const service = TestBed.inject(ThemeService);
    service.toggle();
    TestBed.tick();
    expect(document.documentElement.getAttribute('data-bs-theme')).toBe('light');
    expect(localStorage.getItem('pb-theme')).toBe('light');
  });

  it('starts with the stored theme', () => {
    localStorage.setItem('pb-theme', 'light');
    expect(TestBed.inject(ThemeService).theme()).toBe('light');
  });
});
