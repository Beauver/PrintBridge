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
