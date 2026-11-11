import { Component, inject } from '@angular/core';
import { ThemeService } from '../../core/theme/theme.service';

/** Floating light/dark switch in the bottom-right corner, shown on every page. */
@Component({
  selector: 'app-theme-toggle',
  templateUrl: './theme-toggle.html',
  styleUrl: './theme-toggle.css',
})
export class ThemeToggle {
  protected readonly theme = inject(ThemeService);
}
