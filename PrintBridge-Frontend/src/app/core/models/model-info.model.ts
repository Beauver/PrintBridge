/** PENDING: not analysed yet; FAILED: the file couldn't be read. */
export type ModelInfoStatus = 'PENDING' | 'ANALYZED' | 'FAILED';

/** MESH: STL or plain 3MF; BAMBU: Bambu Studio / OrcaSlicer project. */
export type ModelSource = 'MESH' | 'BAMBU';

/** Where the grams come from: sliced, some plates estimated, or only estimated. */
export type WeightKind = 'EXACT' | 'PARTLY_ESTIMATED' | 'ESTIMATED';

export type SliceStatus = 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED';

export interface FilamentInfo {
  type: string | null;
  /** "#RRGGBB" or null. */
  color: string | null;
  /** Exact usage when sliced, else null. */
  grams: number | null;
}

/** One plate; sizes, grams and `estimated` are null for an empty plate. */
export interface PlateInfo {
  index: number;
  name: string | null;
  width: number | null;
  depth: number | null;
  height: number | null;
  grams: number | null;
  /** True when `grams` is an estimate from the plate's volume. */
  estimated: boolean | null;
  printSeconds: number | null;
  /** Null without printers or for an empty plate. */
  fits: boolean | null;
  filaments: FilamentInfo[];
  /** Why OrcaSlicer refused this plate; only for the admin. */
  sliceError: string | null;
}

/** Server slicing of a file. */
export interface SlicingInfo {
  /** Server slicing is switched on (the admin may re-slice). */
  enabled: boolean;
  /** Null when the file was never queued. */
  status: SliceStatus | null;
  /** Printer the numbers are for. */
  printer: string | null;
  /** Nozzle (mm) the numbers are for. */
  nozzle: number | null;
  /** The project's printer when it isn't owned and the default printer was used. */
  madeForPrinter: string | null;
  /** The project's nozzle when another nozzle was used (not owned, or another printer). */
  madeForNozzle: number | null;
  /** Why the job failed; only for the admin. */
  error: string | null;
}

/** What the backend knows about a model file. Only `status` and `slicing` are set unless ANALYZED. */
export interface ModelInfo {
  status: ModelInfoStatus;
  source: ModelSource | null;
  volumeCm3: number | null;
  /** Sum over the plates with objects (exact and estimated). */
  totalGrams: number | null;
  weight: WeightKind | null;
  printSeconds: number | null;
  /** Whether every plate fits on `fitPrinter`; null without printers. */
  fits: boolean | null;
  /** The printer it was sliced for, else the default printer. */
  fitPrinter: string | null;
  plates: PlateInfo[];
  slicing: SlicingInfo;
}
