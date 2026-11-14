import { Component, inject, OnInit, signal } from '@angular/core';
import { errorMessage } from '../../core/http/api-error';
import { Settings } from '../../core/models/settings.model';
import { SlicerPrinter, SlicerStatus } from '../../core/models/slicer.model';
import { PrintersCard } from './printers-card/printers-card';
import { SettingsService } from './settings.service';
import { SlicingSettingsCard } from './slicing-settings/slicing-settings';
import { UploadSettingsCard } from './upload-settings/upload-settings';

/** Admin page for application settings: the printers, server slicing and the upload limits. */
@Component({
  selector: 'app-settings-page',
  imports: [SlicingSettingsCard, PrintersCard, UploadSettingsCard],
  templateUrl: './settings-page.html',
})
export class SettingsPage implements OnInit {
  private readonly settingsService = inject(SettingsService);

  protected readonly settings = signal<Settings | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  /** Null while it is being checked. */
  protected readonly slicerStatus = signal<SlicerStatus | null>(null);
  /** What the slicer offers; empty when it can't be reached. */
  protected readonly slicerPrinters = signal<SlicerPrinter[]>([]);

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
