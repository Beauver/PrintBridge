/** PENDING: not analysed yet; FAILED: the file couldn't be read. */
export type ModelInfoStatus = 'PENDING' | 'ANALYZED' | 'FAILED';

/** MESH: STL or plain 3MF; BAMBU: Bambu Studio / OrcaSlicer project; PRUSA: PrusaSlicer project. */
export type ModelSource = 'MESH' | 'BAMBU' | 'PRUSA';

export interface FilamentInfo {
  type: string | null;
  /** "#RRGGBB" or null. */
  color: string | null;
  /** Exact usage from a sliced project, else null. */
  grams: number | null;
}

/** One plate; sizes are null for an empty plate. */
export interface PlateInfo {
  index: number;
  name: string | null;
  width: number | null;
  depth: number | null;
  height: number | null;
  /** Exact weight from a sliced project, else null. */
  grams: number | null;
  printSeconds: number | null;
  /** Null when no build plate size is set or the plate is empty. */
  fits: boolean | null;
  filaments: FilamentInfo[];
}

/** What the backend read from a model file. Only `status` is set unless it is ANALYZED. */
export interface ModelInfo {
  status: ModelInfoStatus;
  source: ModelSource | null;
  volumeCm3: number | null;
  /** Rough weight of the whole model, from its volume. */
  estimatedGrams: number | null;
  /** Exact total; only when every plate with objects is sliced. */
  slicedGrams: number | null;
  printSeconds: number | null;
  /** Whether every plate fits; null when no build plate size is set. */
  fits: boolean | null;
  plates: PlateInfo[];
}
