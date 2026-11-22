package com.beauver.printbridge.filamentchoice.dto;

import com.beauver.printbridge.filamentchoice.ChoiceSource;

import java.time.Instant;
import java.util.List;

/**
 * A chosen filament with how it stands now.
 *
 * @param wholeItem     for the whole item (a file without colours, or a link); then the plate and
 *                      original colour are {@code null}
 * @param plateIndex    {@code null} for the whole item
 * @param originalType  the colour's type in the file; {@code null} for the whole item or when unknown
 * @param originalColor the colour in the file ({@code #RRGGBB}); {@code null} for the whole item or when unknown
 * @param name          the filament as it was when chosen (name, vendor, material, colours)
 * @param colors        a multi-colour filament's colours; otherwise empty
 * @param orphaned      the file no longer has this colour on this plate (e.g. after a re-slice)
 * @param inSpoolman    whether Spoolman still has the filament; {@code null} when unknown (integration
 *                      off or Spoolman unreachable)
 * @param gramsLeft     what is left of it in Spoolman now; {@code null} when unknown
 * @param gramsNeeded   what the request needs of this filament: every item using it times its quantity,
 *                      without declined items; {@code null} when unknown
 * @param notEnough     less is left than needed (or nothing at all): it will be ordered
 * @param source        a person ({@code MANUAL}) or automatic matching ({@code AUTO}, #76)
 */
public record FilamentChoiceDto(
        Long id,
        boolean wholeItem,
        Integer plateIndex,
        String originalType,
        String originalColor,
        long spoolmanFilamentId,
        String name,
        String vendor,
        String material,
        String color,
        List<String> colors,
        Instant chosenAt,
        boolean orphaned,
        Boolean inSpoolman,
        Double gramsLeft,
        Double gramsNeeded,
        boolean notEnough,
        ChoiceSource source) {
}
