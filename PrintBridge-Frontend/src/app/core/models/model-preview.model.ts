/** The printer bed a preview is placed on, in whole mm. */
export interface PreviewBed {
  x: number;
  y: number;
  z: number;
}

/** A plate of the model file, also when it is empty. */
export interface PreviewPlate {
  index: number;
  name: string | null;
}

/** One coloured mesh on a plate, placed on the bed (z up, mm). */
export interface PreviewPart {
  plate: number;
  /** "#RRGGBB" filament colour; null for the viewer's neutral colour. */
  color: string | null;
  /** x, y, z per vertex. */
  positions: Float32Array;
  /** Three vertex indices per triangle. */
  indices: Uint32Array;
}

/** What the 3D preview shows of a model file (#13). */
export interface PreviewMesh {
  /** Null when no printer is set up; the model is then centred on 0, 0. */
  bed: PreviewBed | null;
  plates: PreviewPlate[];
  parts: PreviewPart[];
}
