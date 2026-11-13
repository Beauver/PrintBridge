package com.beauver.printbridge.slicing;

import com.beauver.printbridge.common.error.ConflictException;
import com.beauver.printbridge.common.transaction.AfterTransaction;
import com.beauver.printbridge.modelinfo.ModelInfo;
import com.beauver.printbridge.modelinfo.ModelInfoRepository;
import com.beauver.printbridge.modelinfo.ModelInfoStatus;
import com.beauver.printbridge.modelinfo.analysis.FilamentUsage;
import com.beauver.printbridge.modelinfo.analysis.ModelSource;
import com.beauver.printbridge.modelinfo.analysis.PlateAnalysis;
import com.beauver.printbridge.modelinfo.analysis.ProjectSettings;
import com.beauver.printbridge.printrequest.ModelFileType;
import com.beauver.printbridge.printrequest.RequestFile;
import com.beauver.printbridge.settings.SettingsService;
import com.beauver.printbridge.settings.SettingsService.SlicingToggled;
import com.beauver.printbridge.settings.SlicingSettings;
import com.beauver.printbridge.slicing.client.SliceResponse;
import com.beauver.printbridge.storage.FileStorage;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Server slicing (#15): queues files, hands jobs to the {@link SliceWorker} one at a time and
 * stores the results. The queue is the {@code slice_results} table, so it survives restarts.
 */
@ApplicationScoped
public class SlicingService {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Inject
    SliceResultRepository results;

    @Inject
    ModelInfoRepository modelInfos;

    @Inject
    SettingsService settings;

    @Inject
    SlicePlanner planner;

    @Inject
    FileStorage storage;

    @Inject
    EntityManager entityManager;

    @Inject
    AfterTransaction afterTransaction;

    @Inject
    SliceWorker worker;

    /** Queues a new upload while server slicing is on. Runs inside the upload's transaction. */
    @Transactional(Transactional.TxType.MANDATORY)
    public void enqueueIfEnabled(RequestFile file) {
        if (settings.slicing().enabled()) {
            queue(file.id);
        }
    }

    /** Slices these files again (admin). Refused while slicing is off or a file is already in the queue. */
    @Transactional(Transactional.TxType.MANDATORY)
    public void reslice(Collection<RequestFile> files) {
        if (!settings.slicing().enabled()) {
            throw new ConflictException("Server slicing is off");
        }
        for (RequestFile file : files) {
            Optional<SliceResult> existing = results.findByIdOptional(file.id);
            if (existing.isPresent() && existing.get().status.isPending()) {
                throw new ConflictException("This file is already being sliced");
            }
        }
        files.forEach(file -> queue(file.id));
    }

    public Optional<SliceResult> find(long fileId) {
        return results.findByIdOptional(fileId);
    }

    /** Switching slicing off drops waiting jobs (a running one finishes); switching it on starts the worker. */
    void onToggle(@Observes SlicingToggled toggled) {
        if (toggled.enabled()) {
            afterTransaction.onCommit(worker::wake);
        } else {
            results.deleteQueued();
        }
    }

    /** Puts jobs that were running when the backend stopped back into the queue. */
    @Transactional
    public void requeueInterrupted() {
        int count = results.requeueRunning();
        if (count > 0) {
            Log.infof("Re-queued %d interrupted slicing job(s)", count);
        }
    }

    /**
     * Takes the oldest queued job and marks it RUNNING. A job that can't be prepared (file
     * missing, no model details) fails right away and the next one is tried.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Optional<SliceJob> claimNext() {
        SlicingSettings slicing = settings.slicing();
        if (!slicing.enabled() || slicing.defaultPrinter() == null) {
            return Optional.empty();
        }
        Optional<SliceResult> next = results.nextQueued();
        while (next.isPresent()) {
            SliceResult result = next.get();
            Optional<SliceJob> job = prepare(result, slicing);
            if (job.isPresent()) {
                result.status = SliceStatus.RUNNING;
                return job;
            }
            next = results.nextQueued();
        }
        return Optional.empty();
    }

    private Optional<SliceJob> prepare(SliceResult result, SlicingSettings slicing) {
        RequestFile file = entityManager.find(RequestFile.class, result.fileId);
        Path path = null;
        if (file != null) {
            path = storage.resolve(file.storagePath);
        }
        if (file == null || !Files.isRegularFile(path)) {
            markFailed(result, "The model file is missing on disk");
            return Optional.empty();
        }
        ModelInfo info = modelInfos.findById(result.fileId);
        ModelSource source = ModelSource.MESH;
        List<Integer> plates = List.of();
        if (info != null && info.status == ModelInfoStatus.ANALYZED) {
            source = info.source;
            plates = info.plates.stream().filter(plate -> plate.size() != null).map(PlateAnalysis::index).toList();
        } else if (file.fileType == ModelFileType.THREE_MF && info == null) {
            markFailed(result, "Model details aren't available yet; try again in a moment");
            return Optional.empty();
        }
        Optional<ProjectSettings> project = Optional.empty();
        if (source == ModelSource.BAMBU) {
            project = ProjectSettings.read(path);
        }
        SlicePlan plan = planner.plan(slicing, file.fileType, source, plates, project);
        return Optional.of(new SliceJob(file.id, path, plan, slicing.timeout()));
    }

    /** Stores what the slicer returned. Ignored when the file was deleted in the meantime. */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void complete(long fileId, SlicePlan plan, SliceResponse response) {
        results.findByIdOptional(fileId).ifPresent(result -> {
            result.status = SliceStatus.DONE;
            result.printer = plan.printer();
            result.nozzle = plan.nozzle();
            result.madeForPrinter = plan.madeForPrinter();
            result.madeForNozzle = plan.madeForNozzle();
            result.plates = response.plates().stream().map(SlicingService::toPlate).toList();
            result.error = null;
            result.finishedAt = Instant.now();
        });
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void fail(long fileId, String message) {
        results.findByIdOptional(fileId).ifPresent(result -> markFailed(result, message));
    }

    private void queue(long fileId) {
        SliceResult result = results.findById(fileId);
        if (result == null) {
            result = new SliceResult();
            result.fileId = fileId;
        }
        result.status = SliceStatus.QUEUED;
        result.printer = null;
        result.nozzle = null;
        result.madeForPrinter = null;
        result.madeForNozzle = null;
        result.plates = null;
        result.error = null;
        result.queuedAt = Instant.now();
        result.finishedAt = null;
        results.persist(result);
        afterTransaction.onCommit(worker::wake);
    }

    private static void markFailed(SliceResult result, String message) {
        Log.warnf("Slicing file %d failed: %s", result.fileId, message);
        result.status = SliceStatus.FAILED;
        result.plates = null;
        result.error = truncate(message);
        result.finishedAt = Instant.now();
    }

    private static SlicedPlate toPlate(SliceResponse.Plate plate) {
        List<FilamentUsage> filaments = List.of();
        if (plate.filaments() != null) {
            filaments = plate.filaments().stream()
                    .map(filament -> FilamentUsage.cleaned(filament.type(), filament.color(), filament.grams()))
                    .toList();
        }
        return new SlicedPlate(plate.index(), plate.grams(), plate.seconds(), filaments, truncate(plate.error()));
    }

    private static String truncate(String message) {
        if (message == null || message.length() <= MAX_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_LENGTH);
    }
}
