import { Component, effect, input, output, signal, untracked } from '@angular/core';
import { Observable } from 'rxjs';
import { errorMessage } from '../../core/http/api-error';
import { PreviewColor, PreviewMesh } from '../../core/models/model-preview.model';
import { ModelViewer } from './model-viewer';

/**
 * The "Show 3D preview" button and what follows: a spinner, the viewer, or why there is no
 * preview. Nothing loads until the button is clicked (#13).
 */
@Component({
  selector: 'app-model-preview',
  imports: [ModelViewer],
  templateUrl: './model-preview.html',
  styleUrl: './model-preview.css',
})
export class ModelPreview {
  /** Loads the mesh; a new loader (another file) resets the preview. */
  readonly loader = input.required<() => Observable<PreviewMesh>>();
  /** Image shown until the preview is loaded, e.g. the plate image of a Bambu file. */
  readonly placeholder = input<string | null>(null);
  /** Filaments chosen for the file's colours (#11), passed on to the viewer. */
  readonly colors = input<readonly PreviewColor[]>([]);
  /** The plate the viewer shows; projected content appears under its plate buttons. */
  readonly plateChange = output<number>();

  protected readonly mesh = signal<PreviewMesh | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      this.loader();
      untracked(() => {
        this.mesh.set(null);
        this.loading.set(false);
        this.error.set(null);
      });
    });
  }

  protected show(): void {
    this.loading.set(true);
    this.error.set(null);
    this.loader()().subscribe({
      next: (mesh) => {
        this.loading.set(false);
        this.mesh.set(mesh);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(errorMessage(err, 'No preview available for this file'));
      },
    });
  }

  protected onPlateChange(plate: number): void {
    this.plateChange.emit(plate);
  }
}
