/** A printer's build volume in whole mm. */
export interface BuildPlate {
  x: number;
  y: number;
  z: number;
}

/** Server slicing on/off and how long one file may take. */
export interface SlicingSettings {
  enabled: boolean;
  timeoutMinutes: number;
}

/** A printer to add or change; nozzles and profiles may stay empty while slicing is off. */
export interface PrinterInput {
  /** As OrcaSlicer names it (e.g. "Bambu Lab P1S"), or the admin's own name. */
  model: string;
  buildPlate: BuildPlate;
  ownedNozzles: number[];
  /** Nozzle for STLs and for projects made for a nozzle that isn't owned. */
  defaultNozzle: number | null;
  process: string | null;
  filament: string | null;
}

/** A printer the admin owns. */
export interface Printer extends PrinterInput {
  id: number;
}

/** The upload limits the admin sets. */
export interface UploadSettings {
  /** 1–50. */
  maxFiles: number;
  /** 1 up to `ceilingMb`. */
  maxFileSizeMb: number;
}

/** The upload limits that apply now. */
export interface UploadLimits extends UploadSettings {
  /** The server's ceiling per file (UPLOAD_MAX_SIZE_MB); only raised through the environment. */
  ceilingMb: number;
}

export interface Settings {
  slicing: SlicingSettings;
  /** For STL files and projects made for a printer that isn't owned; null without printers. */
  defaultPrinterId: number | null;
  /** In the order they were added. */
  printers: Printer[];
  uploads: UploadLimits;
}
