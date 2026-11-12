/** Application settings; the build plate size (mm) is null until the admin sets it. */
export interface Settings {
  buildPlateX: number | null;
  buildPlateY: number | null;
  buildPlateZ: number | null;
}
