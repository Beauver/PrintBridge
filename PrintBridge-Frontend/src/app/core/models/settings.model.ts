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

/** The Spoolman settings (#11); colour selection needs the integration. */
export interface SpoolmanSettings {
  enabled: boolean;
  colorSelection: boolean;
  /** The price estimate uses the chosen filaments' Spoolman prices (#72); needs the integration. */
  prices: boolean;
  /** Colours of new projects get the closest filament automatically (#76); needs colour selection. */
  autoMatch: boolean;
  /** The largest colour difference (CIEDE2000 ΔE, 1–100) automatic matching accepts. */
  matchTolerance: number;
  /** The warning for colours without a close filament; the default text unless the admin set one. */
  noMatchMessage: string;
  /** The warning for a filament that runs short; the default text unless the admin set one. */
  notEnoughMessage: string;
}

/** The price estimate (#72); whether Spoolman's prices are used is a Spoolman setting. */
export interface PricingSettings {
  /** Multiplies every price (0.01–100). */
  markup: number;
  /** EUR per 100 g: the default for everything without a Spoolman price; 0 = none. */
  pricePer100Grams: number;
}

export interface Settings {
  slicing: SlicingSettings;
  /** For STL files and projects made for a printer that isn't owned; null without printers. */
  defaultPrinterId: number | null;
  /** In the order they were added. */
  printers: Printer[];
  uploads: UploadLimits;
  spoolman: SpoolmanSettings;
  pricing: PricingSettings;
}
