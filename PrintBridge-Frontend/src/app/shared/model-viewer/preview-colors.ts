import { PreviewColor, PreviewPart } from '../../core/models/model-preview.model';

/**
 * The colour to draw a part in when filaments were chosen for the file's colours (#11); null to
 * keep the part's own colour. Matched by plate and colour only: two materials in the same colour
 * on one plate both change.
 *
 * A part without a colour of its own (STL, plain 3MF) takes the whole file's choice, or else the
 * plate's choice when the plate has exactly one (the slicer gave it one colour).
 */
export function chosenColor(part: PreviewPart, colors: readonly PreviewColor[]): string | null {
  const onPlate = colors.filter((color) => {
    return color.plate === part.plate;
  });
  if (part.color !== null) {
    const own = part.color.toLowerCase();
    const match = onPlate.find((color) => {
      return color.from?.toLowerCase() === own;
    });
    return match?.to ?? null;
  }
  const wholeFile = colors.find((color) => {
    return color.plate === null;
  });
  if (wholeFile) {
    return wholeFile.to;
  }
  if (onPlate.length === 1) {
    return onPlate[0].to;
  }
  return null;
}
