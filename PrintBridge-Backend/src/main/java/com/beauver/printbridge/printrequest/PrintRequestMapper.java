package com.beauver.printbridge.printrequest;

import com.beauver.printbridge.printrequest.dto.PrintRequestDto;
import com.beauver.printbridge.printrequest.dto.PrintRequestSummaryDto;
import com.beauver.printbridge.printrequest.dto.RequestFileDto;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PrintRequestMapper {

    public PrintRequestSummaryDto toSummary(PrintRequest r) {
        return new PrintRequestSummaryDto(r.id, r.name, r.status, r.finalPrice, r.paidAt, r.owner.username,
                r.createdAt);
    }

    public PrintRequestDto toDto(PrintRequest r) {
        return new PrintRequestDto(r.id, r.name, r.description, r.status, r.finalPrice, r.declineReason, r.paidAt,
                r.owner.id, r.owner.username, r.createdAt, r.updatedAt,
                r.files.stream().map(this::toDto).toList(), r.status.nextStatuses());
    }

    public RequestFileDto toDto(RequestFile f) {
        return new RequestFileDto(f.id, f.originalFilename, f.fileType, f.sizeBytes, f.createdAt);
    }
}
