import { InjectionToken } from '@angular/core';
import { PreviewMesh } from '../../core/models/model-preview.model';

/** What the viewer component needs from a scene; replaced by a fake in tests. */
export interface PreviewSceneHandle {
  show(mesh: PreviewMesh, plate: number): void;
  dispose(): void;
}

export interface PreviewSceneFactory {
  /** Whether the browser can draw WebGL at all. */
  isAvailable(): boolean;
  create(host: HTMLElement): Promise<PreviewSceneHandle>;
}

/**
 * Three.js is only downloaded when a preview is actually shown: the scene module is imported
 * here on demand, so pages with a Model card don't pay for it.
 */
export const PREVIEW_SCENE_FACTORY = new InjectionToken<PreviewSceneFactory>(
  'PREVIEW_SCENE_FACTORY',
  {
    providedIn: 'root',
    factory: () => {
      return {
        isAvailable: isWebGlAvailable,
        create: async (host: HTMLElement) => {
          const { PreviewScene } = await import('./preview-scene');
          return new PreviewScene(host);
        },
      };
    },
  },
);

export function isWebGlAvailable(): boolean {
  try {
    const canvas = document.createElement('canvas');
    return !!(canvas.getContext('webgl2') ?? canvas.getContext('webgl'));
  } catch {
    return false;
  }
}
