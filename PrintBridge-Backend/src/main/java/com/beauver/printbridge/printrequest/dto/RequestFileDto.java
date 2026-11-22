package com.beauver.printbridge.printrequest.dto;

import com.beauver.printbridge.filamentchoice.dto.ColorSlotDto;
import com.beauver.printbridge.filamentchoice.dto.FilamentChoiceDto;
import com.beauver.printbridge.modelfile.ModelFileType;
import com.beauver.printbridge.modelinfo.dto.ModelInfoDto;
import com.beauver.printbridge.printrequest.file.ItemStatus;
import com.beauver.printbridge.printrequest.file.RequestFileKind;
import java.time.Instant;
import java.util.List;

/**
 * An entry of a request: an uploaded file or a link (#59).
 *
 * @param originalFilename files only
 * @param type             files only
 * @param sizeBytes        files only
 * @param url              links only
 * @param linkName         links only, optional
 * @param quantity         how many copies to print
 * @param status           where this item is (#61)
 * @param declineReason    why the admin declined it; only while DECLINED, optional
 * @param nextStatuses     statuses the admin may move it to; empty while the request is final
 * @param modelInfo        what the analyser read from the file; {@code null} for links
 * @param filamentChoices  the Spoolman filaments chosen for its colours, or for the whole item (#11)
 * @param unmatchedColors  colours automatic matching found no close filament for, and nobody decided on
 *                         since (#76)
 */
public record RequestFileDto(
        Long id,
        RequestFileKind kind,
        String originalFilename,
        ModelFileType type,
        Long sizeBytes,
        String url,
        String linkName,
        int quantity,
        ItemStatus status,
        String declineReason,
        List<ItemStatus> nextStatuses,
        Instant createdAt,
        ModelInfoDto modelInfo,
        List<FilamentChoiceDto> filamentChoices,
        List<ColorSlotDto> unmatchedColors) {
}
