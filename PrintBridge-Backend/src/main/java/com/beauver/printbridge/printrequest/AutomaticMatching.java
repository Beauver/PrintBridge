package com.beauver.printbridge.printrequest;

import com.beauver.printbridge.common.transaction.Transactions;
import com.beauver.printbridge.filamentchoice.FilamentChoiceService;
import com.beauver.printbridge.modelfile.FilePlatesChanged;
import com.beauver.printbridge.modelfile.analysis.ModelSource;
import com.beauver.printbridge.modelinfo.ModelInfoService;
import com.beauver.printbridge.modelinfo.ModelInfoStatus;
import com.beauver.printbridge.modelinfo.dto.ModelInfoDto;
import com.beauver.printbridge.printrequest.file.RequestFile;
import com.beauver.printbridge.settings.SettingsService;
import com.beauver.printbridge.settings.SpoolmanSettings;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

/**
 * Matches Spoolman filaments to a project's colours as soon as they are known (#76): after the
 * analysis of an upload and after every server slice. Only for Bambu/Orca projects (the colours of
 * an STL come from the slicer profile, not from the requester) of requests that may still change.
 */
@ApplicationScoped
public class AutomaticMatching {

    @Inject
    PrintRequestRepository requests;

    @Inject
    ModelInfoService modelInfo;

    @Inject
    FilamentChoiceService filamentChoices;

    @Inject
    SettingsService settings;

    @Inject
    Transactions transactions;

    /** Never fails the upload or the slice job that fired it. */
    void onPlatesChanged(@Observes FilePlatesChanged changed) {
        try {
            transactions.run(() -> match(changed.fileId()));
        } catch (RuntimeException e) {
            Log.warnf(e, "Matching filaments to file %d failed", changed.fileId());
        }
    }

    void match(long fileId) {
        SpoolmanSettings spoolman = settings.getSpoolman();
        if (!spoolman.autoMatch()) {
            return;
        }
        Optional<RequestFile> file = requests.findFile(fileId);
        if (file.isEmpty() || file.get().isLink() || file.get().request.status.isFinal()) {
            return;
        }
        ModelInfoDto info = modelInfo.describe(List.of(fileId), true).get(fileId);
        if (info == null || info.status() != ModelInfoStatus.ANALYZED || info.source() != ModelSource.BAMBU) {
            return;
        }
        filamentChoices.matchAutomatically(fileId, info, spoolman.matchTolerance());
    }
}
