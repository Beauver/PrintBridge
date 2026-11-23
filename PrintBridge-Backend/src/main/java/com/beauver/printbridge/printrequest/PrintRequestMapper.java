package com.beauver.printbridge.printrequest;

import com.beauver.printbridge.filamentchoice.dto.ColorSlotDto;
import com.beauver.printbridge.filamentchoice.dto.FilamentChoiceDto;
import com.beauver.printbridge.modelinfo.dto.ModelInfoDto;
import com.beauver.printbridge.pricing.PriceEstimate;
import com.beauver.printbridge.printrequest.dto.EstimatedPriceDto;
import com.beauver.printbridge.printrequest.dto.PrintRequestDto;
import com.beauver.printbridge.printrequest.dto.PrintRequestSummaryDto;
import com.beauver.printbridge.printrequest.dto.RequestFileDto;
import com.beauver.printbridge.printrequest.dto.RequestTotalsDto;
import com.beauver.printbridge.printrequest.file.ItemStatus;
import com.beauver.printbridge.printrequest.file.RequestFile;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;

/** Print requests as the API returns them. What isn't on the entities is handed in by the service. */
@ApplicationScoped
public class PrintRequestMapper {

    /**
     * @param estimate the automatic price (#72); {@code null} for none
     * @param currency ISO 4217 code of the prices (#75)
     */
    public PrintRequestSummaryDto toSummary(PrintRequest r, ItemProgress progress, PriceEstimate estimate,
                                            String currency) {
        return new PrintRequestSummaryDto(r.id, r.name, r.status, r.finalPrice, r.paidAt, r.owner.username,
                r.createdAt, progress.completed(), progress.active(), toDto(estimate), currency);
    }

    /**
     * @param filamentSelection        the caller may choose filaments now (#11)
     * @param notEnoughFilamentMessage the warning for a chosen filament that runs short (#11)
     * @param estimate                 the automatic price (#72); {@code null} for none
     * @param currency                 ISO 4217 code of the prices (#75)
     */
    public PrintRequestDto toDto(PrintRequest r, List<RequestFileDto> files, RequestTotalsDto totals,
                                 boolean filamentSelection, String notEnoughFilamentMessage, String noMatchMessage,
                                 PriceEstimate estimate, String currency) {
        return new PrintRequestDto(r.id, r.name, r.description, r.status, r.finalPrice, r.declineReason, r.paidAt,
                r.owner.id, r.owner.username, r.createdAt, r.updatedAt, files, totals, r.status.nextStatuses(),
                filamentSelection, notEnoughFilamentMessage, noMatchMessage, toDto(estimate), currency);
    }

    public EstimatedPriceDto toDto(PriceEstimate estimate) {
        if (estimate == null) {
            return null;
        }
        return new EstimatedPriceDto(estimate.amount(), estimate.partial());
    }

    /**
     * @param modelInfos      what is known about each file, by file id; links have none
     * @param filamentChoices the chosen filaments of each item, by item id; items without any are left out
     * @param unmatched       the colours without a close filament (#76), by item id; likewise
     */
    public List<RequestFileDto> toFileDtos(List<RequestFile> files, Map<Long, ModelInfoDto> modelInfos,
                                           Map<Long, List<FilamentChoiceDto>> filamentChoices,
                                           Map<Long, List<ColorSlotDto>> unmatched) {
        return files.stream()
                .map(file -> toDto(file, modelInfos.get(file.id), filamentChoices.getOrDefault(file.id, List.of()),
                        unmatched.getOrDefault(file.id, List.of())))
                .toList();
    }

    public RequestFileDto toDto(RequestFile f, ModelInfoDto modelInfo, List<FilamentChoiceDto> filamentChoices,
                                List<ColorSlotDto> unmatched) {
        List<ItemStatus> next = List.of();
        if (!f.request.status.isFinal()) {
            next = f.status.nextStatuses();
        }
        return new RequestFileDto(f.id, f.kind, f.originalFilename, f.fileType, f.sizeBytes, f.url, f.linkName,
                f.quantity, f.status, f.declineReason, next, f.createdAt, modelInfo, filamentChoices, unmatched);
    }
}
