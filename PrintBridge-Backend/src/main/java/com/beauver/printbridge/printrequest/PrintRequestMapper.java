package com.beauver.printbridge.printrequest;

import com.beauver.printbridge.printrequest.dto.PrintRequestDto;
import com.beauver.printbridge.printrequest.dto.PrintRequestSummaryDto;
import com.beauver.printbridge.printrequest.dto.RequestFileDto;
import com.beauver.printbridge.modelinfo.ModelInfoService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PrintRequestMapper {

    @Inject
    ModelInfoService modelInfo;

    public PrintRequestSummaryDto toSummary(PrintRequest r) {
        return new PrintRequestSummaryDto(r.id, r.name, r.status, r.finalPrice, r.paidAt, r.owner.username,
                r.createdAt);
    }

    /** @param admin whether the caller is the admin (sees slicing errors) */
    public PrintRequestDto toDto(PrintRequest r, boolean admin) {
        return new PrintRequestDto(r.id, r.name, r.description, r.status, r.finalPrice, r.declineReason, r.paidAt,
                r.owner.id, r.owner.username, r.createdAt, r.updatedAt,
                r.files.stream().map(file -> toDto(file, admin)).toList(), r.status.nextStatuses());
    }

    public RequestFileDto toDto(RequestFile f, boolean admin) {
        return new RequestFileDto(f.id, f.originalFilename, f.fileType, f.sizeBytes, f.createdAt,
                modelInfo.describe(f, admin));
    }
}
