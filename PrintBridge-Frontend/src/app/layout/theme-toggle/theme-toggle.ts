import { Component, inject } from '@angular/core';
import { ThemeService } from '../../core/theme/theme.service';

/** Floating light/dark switch in the bottom-right corner, shown on every page. */
@Component({
  selector: 'app-theme-toggle',
  templateUrl: './theme-toggle.html',
  styles: `
    :host {
      position: fixed;
      right: 1rem;
      bottom: 1rem;
      z-index: 1030;
    }
    button {
      width: 2.75rem;
      height: 2.75rem;
      font-size: 1.15rem;
      line-height: 1;
    }
  `,
})
export class ThemeToggle {
  protected readonly theme = inject(ThemeService);
}
