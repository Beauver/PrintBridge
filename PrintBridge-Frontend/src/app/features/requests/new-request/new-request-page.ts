import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { errorMessage } from '../../../core/http/api-error';
import { UploadConfig } from '../../../core/models';
import { fieldError } from '../../../shared/account-form';
import { formatBytes } from '../../../shared/print-request-display';
import { PrintRequestsService } from '../print-requests.service';

/** Used until the upload config is loaded (or if loading it fails); the backend checks anyway. */
const FALLBACK_CONFIG: UploadConfig = { maxSizeBytes: 0, allowedExtensions: ['stl', '3mf'] };

/** Form to submit a new print request with one model file. */
@Component({
  selector: 'app-new-request-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './new-request-page.html',
})
export class NewRequestPage implements OnInit {
  private readonly requestsService = inject(PrintRequestsService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    description: ['', Validators.maxLength(2000)],
  });

  protected readonly config = signal<UploadConfig>(FALLBACK_CONFIG);
  protected readonly file = signal<File | null>(null);
  /** Set once the user tried to submit, so "file required" isn't shown up front. */
  protected readonly submitted = signal(false);
  protected readonly uploading = signal(false);
  protected readonly progress = signal(0);
  protected readonly error = signal<string | null>(null);

  protected readonly accept = computed(() =>
    this.config()
      .allowedExtensions.map((ext) => `.${ext}`)
      .join(','),
  );

  /** Client-side file check, mirroring the backend (which also checks the content). */
  protected readonly fileError = computed(() => {
    const file = this.file();
    if (!file) {
      return this.submitted() ? 'A model file is required' : null;
    }
    const { allowedExtensions, maxSizeBytes } = this.config();
    const extension = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : '';
    if (!allowedExtensions.includes(extension)) {
      return `Only ${allowedExtensions.map((e) => `.${e}`).join(', ')} files are allowed`;
    }
    if (file.size === 0) {
      return 'The file is empty';
    }
    if (maxSizeBytes > 0 && file.size > maxSizeBytes) {
      return `The file is larger than ${formatBytes(maxSizeBytes)}`;
    }
    return null;
  });

  protected readonly fieldError = fieldError;
  protected readonly formatBytes = formatBytes;

  ngOnInit(): void {
    this.requestsService.uploadConfig().subscribe({
      next: (config) => this.config.set(config),
      // Keep the fallback; the server still enforces its limits.
      error: () => undefined,
    });
  }

  protected onFileSelected(event: Event): void {
    const files = (event.target as HTMLInputElement).files;
    this.file.set(files && files.length > 0 ? files[0] : null);
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    this.submitted.set(true);
    const file = this.file();
    if (this.form.invalid || !file || this.fileError()) {
      return;
    }
    const { name, description } = this.form.getRawValue();
    this.uploading.set(true);
    this.progress.set(0);
    this.error.set(null);
    this.requestsService.create({ name, description, file }).subscribe({
      next: (event) => {
        if (event.type === 'progress') {
          this.progress.set(event.percent);
        } else {
          this.uploading.set(false);
          void this.router.navigate(['/requests', event.request.id]);
        }
      },
      error: (err: unknown) => {
        this.uploading.set(false);
        this.error.set(this.uploadErrorMessage(err));
      },
    });
  }

  private uploadErrorMessage(err: unknown): string {
    // 413 comes from the HTTP layer before the app sees the request, so it has no message body.
    if (err instanceof HttpErrorResponse && err.status === 413) {
      const max = this.config().maxSizeBytes;
      return max > 0 ? `The file is larger than ${formatBytes(max)}` : 'The file is too large';
    }
    return errorMessage(err, 'Could not submit your request.');
  }
}
