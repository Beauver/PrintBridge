import { TestBed } from '@angular/core/testing';
import { ThemeService } from '../../core/theme/theme.service';
import { byCy, click } from '../../core/testing-dom';
import { ThemeToggle } from './theme-toggle';

describe('ThemeToggle', () => {
  beforeEach(() => localStorage.clear());

  it('switches between dark and light', async () => {
    const fixture = TestBed.createComponent(ThemeToggle);
    await fixture.whenStable();
    const theme = TestBed.inject(ThemeService);
    expect(byCy(fixture, 'theme-toggle')!.getAttribute('aria-label')).toBe('Switch to light mode');

    click(fixture, 'theme-toggle');
    await fixture.whenStable();
    expect(theme.theme()).toBe('light');
    expect(byCy(fixture, 'theme-toggle')!.getAttribute('aria-label')).toBe('Switch to dark mode');
  });
});
