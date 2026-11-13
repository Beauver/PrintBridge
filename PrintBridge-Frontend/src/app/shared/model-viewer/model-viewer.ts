import {
  afterNextRender,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  linkedSignal,
  signal,
  viewChild,
} from '@angular/core';
import { PreviewMesh } from '../../core/models/model-preview.model';
import { PREVIEW_SCENE_FACTORY, PreviewSceneHandle } from './preview-scene-factory';

/** A plate button: "1. Lid", "Plate 2", greyed out when empty. */
export interface PlateOption {
  index: number;
  label: string;
  empty: boolean;
}

/**
 * Shows a preview mesh in 3D, one plate at a time (#13). Starts at the first plate with
 * objects; plate buttons only appear for files with more than one plate.
 */
@Component({
  selector: 'app-model-viewer',
  templateUrl: './model-viewer.html',
  styleUrl: './model-viewer.css',
})
export class ModelViewer {
  private readonly factory = inject(PREVIEW_SCENE_FACTORY);

  readonly mesh = input.required<PreviewMesh>();

  private readonly host = viewChild.required<ElementRef<HTMLElement>>('host');
  private readonly scene = signal<PreviewSceneHandle | null>(null);
  protected readonly webGl = signal(true);
  protected readonly ready = computed(() => {
    return this.scene() !== null;
  });
  private destroyed = false;

  protected readonly plates = computed((): PlateOption[] => {
    const mesh = this.mesh();
    return mesh.plates.map((plate) => {
      let label = `Plate ${plate.index}`;
      if (plate.name) {
        label = `${plate.index}. ${plate.name}`;
      }
      const empty = !mesh.parts.some((part) => {
        return part.plate === plate.index;
      });
      return { index: plate.index, label, empty };
    });
  });

  protected readonly showPlateButtons = computed(() => {
    return this.plates().length > 1;
  });

  /** Resets to the first plate with objects whenever another mesh comes in. */
  protected readonly selectedPlate = linkedSignal(() => {
    const plates = this.plates();
    const first =
      plates.find((plate) => {
        return !plate.empty;
      }) ?? plates[0];
    return first?.index ?? 1;
  });

  constructor() {
    afterNextRender(() => {
      if (!this.factory.isAvailable()) {
        this.webGl.set(false);
        return;
      }
      void this.factory.create(this.host().nativeElement).then(
        (scene) => {
          if (this.destroyed) {
            scene.dispose();
            return;
          }
          this.scene.set(scene);
        },
        () => {
          // Loading the 3D code failed (offline?): same message as without WebGL.
          this.webGl.set(false);
        },
      );
    });
    effect(() => {
      const scene = this.scene();
      const mesh = this.mesh();
      const plate = this.selectedPlate();
      scene?.show(mesh, plate);
    });
    inject(DestroyRef).onDestroy(() => {
      this.destroyed = true;
      this.scene()?.dispose();
    });
  }

  protected selectPlate(index: number): void {
    this.selectedPlate.set(index);
  }
}
