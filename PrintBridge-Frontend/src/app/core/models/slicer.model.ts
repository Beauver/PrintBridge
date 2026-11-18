/** Whether the slicer service can be used. */
export interface SlicerStatus {
  configured: boolean;
  reachable: boolean;
  orcaVersion: string | null;
  message: string | null;
}

export interface SlicerNozzle {
  diameter: number;
  machine: string;
  bed: { x: number; y: number; z: number };
}

/** A printer model OrcaSlicer has profiles for. */
export interface SlicerPrinter {
  model: string;
  vendor: string;
  nozzles: SlicerNozzle[];
}

/** Process and filament profiles for one printer + nozzle. */
export interface SlicerProfiles {
  machine: string;
  processes: string[];
  filaments: string[];
}

/** An owned printer the admin can re-slice on (#21). `process`/`filament`: its own profiles. */
export interface ReslicePrinter {
  id: number;
  model: string;
  ownedNozzles: number[];
  defaultNozzle: number;
  process: string | null;
  filament: string | null;
}

/**
 * A choice of printer, nozzle and profiles for one re-slice (#21). A null `process` keeps the
 * project's own settings, a null `filament` the project's filaments.
 */
export interface ResliceChoice {
  printerId: number | null;
  nozzle: number | null;
  process: string | null;
  filament: string | null;
}

/** What the "Re-slice with…" dialog of one file offers. */
export interface ResliceOptions {
  printers: ReslicePrinter[];
  /** A Bambu Studio / OrcaSlicer project with saved settings. */
  project: boolean;
  projectPrinter: string | null;
  projectNozzle: number | null;
  /** Where the dialog starts; `custom` when that is the last custom choice. */
  start: ResliceChoice & { custom: boolean };
}
