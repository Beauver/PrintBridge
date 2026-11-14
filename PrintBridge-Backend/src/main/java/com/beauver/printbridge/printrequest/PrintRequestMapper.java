package com.beauver.printbridge.printrequest;

import com.beauver.printbridge.modelinfo.ModelInfoService;
import com.beauver.printbridge.modelinfo.dto.ModelInfoDto;
import com.beauver.printbridge.modelinfo.dto.WeightKind;
import com.beauver.printbridge.printrequest.dto.PrintRequestDto;
import com.beauver.printbridge.printrequest.dto.PrintRequestSummaryDto;
import com.beauver.printbridge.printrequest.dto.RequestFileDto;
import com.beauver.printbridge.printrequest.dto.RequestTotalsDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

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
        List<RequestFileDto> files = r.files.stream().map(file -> toDto(file, admin)).toList();
        return new PrintRequestDto(r.id, r.name, r.description, r.status, r.finalPrice, r.declineReason, r.paidAt,
                r.owner.id, r.owner.username, r.createdAt, r.updatedAt, files, totals(files), r.status.nextStatuses());
    }

    public RequestFileDto toDto(RequestFile f, boolean admin) {
        return new RequestFileDto(f.id, f.originalFilename, f.fileType, f.sizeBytes, f.quantity, f.createdAt,
                modelInfo.describe(f, admin));
    }

    /**
     * Every file times its quantity. Files without a weight yet (not analysed, unreadable) don't
     * count towards the grams, files without a time (not sliced) not towards the time, which is
     * then marked partial.
     */
    static RequestTotalsDto totals(List<RequestFileDto> files) {
        int copies = files.stream().mapToInt(RequestFileDto::quantity).sum();
        List<RequestFileDto> weighed = files.stream().filter(file -> file.modelInfo().totalGrams() != null).toList();
        Double grams = null;
        WeightKind weight = null;
        if (!weighed.isEmpty()) {
            double sum = weighed.stream()
                    .mapToDouble(file -> file.modelInfo().totalGrams() * file.quantity())
                    .sum();
            grams = Math.round(sum * 100) / 100.0;
            weight = weightKind(weighed);
        }
        List<RequestFileDto> timed = files.stream().filter(file -> file.modelInfo().printSeconds() != null).toList();
        Long printSeconds = null;
        if (!timed.isEmpty()) {
            printSeconds = timed.stream().mapToLong(file -> file.modelInfo().printSeconds() * file.quantity()).sum();
        }
        boolean printTimePartial = !timed.isEmpty() && timed.size() < files.size();
        Boolean fits = null;
        List<Boolean> checked = files.stream().map(file -> file.modelInfo().fits()).filter(fit -> fit != null).toList();
        if (!checked.isEmpty()) {
            fits = checked.stream().allMatch(Boolean::booleanValue);
        }
        return new RequestTotalsDto(files.size(), copies, grams, weight, printSeconds, printTimePartial, fits);
    }

    private static WeightKind weightKind(List<RequestFileDto> weighed) {
        List<WeightKind> kinds = weighed.stream().map(RequestFileDto::modelInfo).map(ModelInfoDto::weight).toList();
        if (kinds.stream().allMatch(kind -> kind == WeightKind.EXACT)) {
            return WeightKind.EXACT;
        }
        if (kinds.stream().allMatch(kind -> kind == WeightKind.ESTIMATED)) {
            return WeightKind.ESTIMATED;
        }
        return WeightKind.PARTLY_ESTIMATED;
    }
}
