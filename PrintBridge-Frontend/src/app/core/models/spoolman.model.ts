/** Whether Spoolman can be used (#11). */
export interface SpoolmanStatus {
  /** SPOOLMAN_URL is set. */
  configured: boolean;
  reachable: boolean;
  /** The configured address; null when not set. */
  url: string | null;
  /** Spoolman's version when reachable. */
  version: string | null;
  /** Why it can't be used, otherwise null. */
  message: string | null;
}

/** A filament in Spoolman that can be chosen. */
export interface SpoolmanFilament {
  id: number;
  name: string | null;
  vendor: string | null;
  material: string | null;
  /** "#RRGGBB"; null for multi-colour filaments or when not set. */
  color: string | null;
  /** The colours of a multi-colour filament; otherwise empty. */
  colors: string[];
  /** Weight left on its spools that aren't archived; null when unknown. */
  gramsLeft: number | null;
}

/** The Spoolman filament chosen for one colour of a plate, or for a whole file or link (#11). */
export interface FilamentChoice {
  id: number;
  /** For the whole item (a file without colours, or a link); then plate and colour are null. */
  wholeItem: boolean;
  /** Null for the whole item. */
  plateIndex: number | null;
  /** The colour's type in the file; null for the whole item or when unknown. */
  originalType: string | null;
  /** The colour in the file ("#RRGGBB"); null for the whole item or when unknown. */
  originalColor: string | null;
  spoolmanFilamentId: number;
  /** The filament as it was when chosen. */
  name: string | null;
  vendor: string | null;
  material: string | null;
  color: string | null;
  colors: string[];
  chosenAt: string;
  /** The file no longer has this colour on this plate (e.g. after a re-slice). */
  orphaned: boolean;
  /** Whether Spoolman still has it; null when unknown (integration off or Spoolman unreachable). */
  inSpoolman: boolean | null;
  /** What is left of it in Spoolman now; null when unknown. */
  gramsLeft: number | null;
  /** What the whole request needs of this filament; null when unknown. */
  gramsNeeded: number | null;
  /** Less is left than needed: it will be ordered. */
  notEnough: boolean;
  /** A person, or automatic matching (#76). */
  source: 'MANUAL' | 'AUTO';
}

/** A colour of a plate as the model info lists it. */
export interface ColorSlot {
  plateIndex: number;
  originalType: string | null;
  originalColor: string;
}

/** What a filament is chosen for: a colour of a plate, or the whole item. */
export interface FilamentSlot {
  /** For the whole item; then plate and colour are null. */
  wholeItem: boolean;
  plateIndex: number | null;
  originalType: string | null;
  originalColor: string | null;
}

/** Body of choosing a filament. */
export interface FilamentChoiceInput extends FilamentSlot {
  spoolmanFilamentId: number;
}
