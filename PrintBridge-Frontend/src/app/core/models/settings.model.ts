/** The printer's build volume in whole mm; null until the admin sets it. */
export interface BuildPlate {
  x: number | null;
  y: number | null;
  z: number | null;
}

/** Server slicing; printer, nozzles and profiles may stay empty while it is off. */
export interface SlicingSettings {
  enabled: boolean;
  timeoutMinutes: number;
  printer: string | null;
  ownedNozzles: number[];
  /** Nozzle for STLs and for projects made for a nozzle that isn't owned. */
  defaultNozzle: number | null;
  process: string | null;
  filament: string | null;
}

export interface Settings {
  buildPlate: BuildPlate;
  slicing: SlicingSettings;
}
