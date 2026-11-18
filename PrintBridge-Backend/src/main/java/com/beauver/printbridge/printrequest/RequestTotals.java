package com.beauver.printbridge.printrequest;

import com.beauver.printbridge.modelinfo.WeightKind;
import com.beauver.printbridge.modelinfo.dto.ModelInfoDto;
import com.beauver.printbridge.printrequest.dto.RequestFileDto;
import com.beauver.printbridge.printrequest.dto.RequestTotalsDto;
import com.beauver.printbridge.printrequest.file.ItemStatus;
import com.beauver.printbridge.printrequest.file.RequestFileKind;

import java.util.List;

/** Adds up the files of a request: copies, filament, print time and whether everything fits (#23). */
public final class RequestTotals {

    private RequestTotals() {
    }

    /**
     * Every file times its quantity. Files without a weight yet (not analysed, unreadable) don't
     * count towards the grams, files without a time (not sliced) not towards the time, which is
     * then marked partial.
     * <p>
     * Links (#59) only count towards the copies. They are marked like a plate that couldn't be
     * sliced: exact grams become partly estimated and the time partial.
     * <p>
     * Declined items (#61) won't be printed, so they count nowhere but in the numbers of files,
     * links and declined items.
     */
    public static RequestTotalsDto of(List<RequestFileDto> entries) {
        int allFiles = (int) entries.stream().filter(entry -> entry.kind() == RequestFileKind.FILE).count();
        int allLinks = entries.size() - allFiles;
        int declined = (int) entries.stream().filter(entry -> entry.status() == ItemStatus.DECLINED).count();
        int completed = (int) entries.stream().filter(entry -> entry.status() == ItemStatus.COMPLETED).count();
        List<RequestFileDto> active = entries.stream().filter(entry -> entry.status() != ItemStatus.DECLINED).toList();
        int copies = active.stream().mapToInt(RequestFileDto::quantity).sum();
        List<RequestFileDto> files = active.stream().filter(entry -> entry.kind() == RequestFileKind.FILE).toList();
        int links = active.size() - files.size();
        List<RequestFileDto> weighed = files.stream().filter(file -> file.modelInfo().totalGrams() != null).toList();
        Double grams = null;
        WeightKind weight = null;
        if (!weighed.isEmpty()) {
            double sum = weighed.stream()
                    .mapToDouble(file -> file.modelInfo().totalGrams() * file.quantity())
                    .sum();
            grams = Math.round(sum * 100) / 100.0;
            weight = weightKind(weighed);
            if (links > 0 && weight == WeightKind.EXACT) {
                weight = WeightKind.PARTLY_ESTIMATED;
            }
        }
        List<RequestFileDto> timed = files.stream().filter(file -> file.modelInfo().printSeconds() != null).toList();
        Long printSeconds = null;
        if (!timed.isEmpty()) {
            printSeconds = timed.stream().mapToLong(file -> file.modelInfo().printSeconds() * file.quantity()).sum();
        }
        boolean printTimePartial = !timed.isEmpty() && timed.size() < active.size();
        Boolean fits = null;
        List<Boolean> checked = files.stream().map(file -> file.modelInfo().fits()).filter(fit -> fit != null).toList();
        if (!checked.isEmpty()) {
            fits = checked.stream().allMatch(Boolean::booleanValue);
        }
        return new RequestTotalsDto(allFiles, allLinks, declined, completed, copies, grams, weight, printSeconds,
                printTimePartial, fits);
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
