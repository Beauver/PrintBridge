import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { errorMessage } from '../../core/http/api-error';
import { Settings } from '../../core/models/settings.model';
import { SlicerPrinter, SlicerStatus } from '../../core/models/slicer.model';
import { SpoolmanStatus } from '../../core/models/spoolman.model';
import { PrintersCard } from './printers-card/printers-card';
import { SettingsService } from './settings.service';
import { SlicingSettingsCard } from './slicing-settings/slicing-settings';
import { SpoolmanSettingsCard } from './spoolman-settings/spoolman-settings';
import { UploadSettingsCard } from './upload-settings/upload-settings';

export type SettingsTab = 'printers' | 'slicing' | 'uploads' | 'spoolman';

export const SETTINGS_TABS: { id: SettingsTab; label: string }[] = [
  { id: 'printers', label: 'Printers' },
  { id: 'slicing', label: 'Slicing' },
  { id: 'uploads', label: 'Uploads' },
  { id: 'spoolman', label: 'Spoolman' },
];

/**
 * Admin page for application settings, one tab per card: the printers, server slicing, the
 * upload limits and Spoolman. The open tab is in the URL (`?tab=spoolman`).
 */
@Component({
  selector: 'app-settings-page',
  imports: [SlicingSettingsCard, PrintersCard, UploadSettingsCard, SpoolmanSettingsCard],
  templateUrl: './settings-page.html',
})
export class SettingsPage implements OnInit {
  private readonly settingsService = inject(SettingsService);
  private readonly router = inject(Router);

  /** From the `tab` query parameter (router input binding). */
  readonly tab = input<string | undefined>(undefined);

  protected readonly tabs = SETTINGS_TABS;
  /** The open tab; an unknown or missing `tab` opens the first. */
  protected readonly activeTab = computed<SettingsTab>(() => {
    const found = SETTINGS_TABS.find((tab) => {
      return tab.id === this.tab();
    });
    if (found) {
      return found.id;
    }
    return 'printers';
  });

  protected readonly settings = signal<Settings | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  /** Null while it is being checked. */
  protected readonly slicerStatus = signal<SlicerStatus | null>(null);
  /** What the slicer offers; empty when it can't be reached. */
  protected readonly slicerPrinters = signal<SlicerPrinter[]>([]);
  /** Null while it is being checked. */
  protected readonly spoolmanStatus = signal<SpoolmanStatus | null>(null);

  ngOnInit(): void {
    this.settingsService.get().subscribe({
      next: (settings) => {
        this.settings.set(settings);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(errorMessage(err, 'Could not load the settings.'));
        this.loading.set(false);
      },
    });
    this.settingsService.slicerStatus().subscribe({
      next: (status) => {
        this.slicerStatus.set(status);
        if (status.reachable) {
          this.loadSlicerPrinters();
        }
      },
      error: () => {
        this.slicerStatus.set({
          configured: false,
          reachable: false,
          orcaVersion: null,
          message: 'Could not check the slicer',
        });
      },
    });
    this.settingsService.spoolmanStatus().subscribe({
      next: (status) => {
        this.spoolmanStatus.set(status);
      },
      error: () => {
        this.spoolmanStatus.set({
          configured: true,
          reachable: false,
          url: null,
          version: null,
          message: 'Could not check Spoolman',
        });
      },
    });
  }

  /** Opens a tab and keeps it in the URL, without a new history entry per click. */
  protected selectTab(tab: SettingsTab): void {
    void this.router.navigate([], {
      queryParams: { tab },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  /** Every change answers with all settings; keep the latest. */
  protected onChanged(settings: Settings): void {
    this.settings.set(settings);
  }

  private loadSlicerPrinters(): void {
    this.settingsService.slicerPrinters().subscribe({
      next: (printers) => {
        this.slicerPrinters.set(printers);
      },
      error: (err: unknown) => {
        this.error.set(errorMessage(err, 'Could not load the printers from the slicer.'));
      },
    });
  }
}
