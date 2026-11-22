package com.beauver.printbridge.pricing;

import com.beauver.printbridge.filamentchoice.dto.FilamentChoiceDto;
import com.beauver.printbridge.modelinfo.dto.ModelInfoDto;

import java.util.List;

/**
 * A file or link of a request as the price estimate needs it.
 *
 * @param quantity copies to print
 * @param declined declined items aren't printed, so they cost nothing
 * @param info     what is known about the file; {@code null} for a link
 * @param choices  the Spoolman filaments chosen for its colours (#11)
 */
public record PricedItem(int quantity, boolean declined, ModelInfoDto info, List<FilamentChoiceDto> choices) {
}
