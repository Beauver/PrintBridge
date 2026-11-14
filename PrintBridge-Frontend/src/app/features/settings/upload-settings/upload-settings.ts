import { Component, inject, input, OnInit, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { errorMessage } from '../../../core/http/api-error';
import { Settings, UploadLimits } from '../../../core/models/settings.model';
import { SettingsService } from '../settings.service';

/** Same limits as the backend; the size's maximum is the server's ceiling. */
export const MIN_FILES = 1;
export const MAX_FILES = 50;
export const MIN_FILE_SIZE_MB = 1;

/**
 * Upload limits (#23): how many files a request may hold and how large each may be, up to the
 * server's ceiling. The allowed extensions stay in the environment.
 */
@Component({
  selector: 'app-upload-settings',
  imports: [ReactiveFormsModule],
  templateUrl: './upload-settings.html',
})
export class UploadSettingsCard implements OnInit {
  private readonly settingsService = inject(SettingsService);
  private readonly fb = inject(FormBuilder);

  readonly settings = input.required<UploadLimits>();
  readonly saved = output<Settings>();

  protected readonly form = this.fb.nonNullable.group({
    maxFiles: ['10', [Validators.required, Validators.pattern(/^\d+$/)]],
    maxFileSizeMb: ['100', [Validators.required, Validators.pattern(/^\d+$/)]],
  });
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly minFiles = MIN_FILES;
  protected readonly maxFiles = MAX_FILES;
  protected readonly minFileSizeMb = MIN_FILE_SIZE_MB;

  ngOnInit(): void {
    const settings = this.settings();
    this.form.setValue({
      maxFiles: String(settings.maxFiles),
      maxFileSizeMb: String(settings.maxFileSizeMb),
    });
  }

  protected save(): void {
    this.form.markAllAsTouched();
    this.notice.set(null);
    this.error.set(null);
    const problem = this.problem();
    if (problem) {
      this.error.set(problem);
      return;
    }
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.settingsService
      .updateUploads({
        maxFiles: Number(value.maxFiles),
        maxFileSizeMb: Number(value.maxFileSizeMb),
      })
      .subscribe({
        next: (settings) => {
          this.saving.set(false);
          this.notice.set('Upload settings saved.');
          this.saved.emit(settings);
        },
        error: (err: unknown) => {
          this.saving.set(false);
          this.error.set(errorMessage(err, 'Could not save the upload settings.'));
        },
      });
  }

  /** What's wrong with the input, mirroring the backend; null when it can be saved. */
  private problem(): string | null {
    const value = this.form.getRawValue();
    const maxFiles = Number(value.maxFiles);
    if (this.form.controls.maxFiles.invalid || maxFiles < MIN_FILES || maxFiles > MAX_FILES) {
      return `Max files must be a whole number from ${MIN_FILES} to ${MAX_FILES}`;
    }
    const ceiling = this.settings().ceilingMb;
    const size = Number(value.maxFileSizeMb);
    if (this.form.controls.maxFileSizeMb.invalid || size < MIN_FILE_SIZE_MB || size > ceiling) {
      return `Max size per file must be a whole number of MB from ${MIN_FILE_SIZE_MB} to ${ceiling}`;
    }
    return null;
  }
}
