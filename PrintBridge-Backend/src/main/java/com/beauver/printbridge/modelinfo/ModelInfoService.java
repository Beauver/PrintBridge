package com.beauver.printbridge.modelinfo;

import com.beauver.printbridge.common.transaction.Transactions;
import com.beauver.printbridge.modelfile.analysis.ModelAnalysis;
import com.beauver.printbridge.modelfile.ModelAnalyzer;
import com.beauver.printbridge.modelfile.StoredModelFile;
import com.beauver.printbridge.modelfile.StoredModelFiles;
import com.beauver.printbridge.modelinfo.dto.ModelInfoDto;
import com.beauver.printbridge.settings.SettingsService;
import com.beauver.printbridge.slicing.SlicingService;
import com.beauver.printbridge.slicing.result.SliceResult;
import com.beauver.printbridge.storage.FileStorage;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Analyses model files and serves the result. Analysis never fails the caller: a file the
 * analyser can't read is stored as FAILED, so an upload always succeeds once it passed
 * validation.
 */
@ApplicationScoped
public class ModelInfoService {

    @Inject
    ModelAnalyzer analyzer;

    @Inject
    ModelInfoRepository repository;

    @Inject
    ModelDetailsCalculator calculator;

    @Inject
    ModelInfoMapper mapper;

    @Inject
    SettingsService settings;

    @Inject
    FileStorage storage;

    @Inject
    StoredModelFiles files;

    @Inject
    SlicingService slicing;

    @Inject
    Transactions transactions;

    /**
     * Analyses a stored file that has no result yet and saves it (new uploads, startup backfill).
     * Does nothing when the file is gone or already has a result.
     * <p>
     * Never part of a caller's transaction: reading a large model takes a while, so the file is
     * looked up and the result saved in two short transactions with the analysis in between.
     */
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public void analyze(long fileId) {
        Optional<StoredModelFile> file = transactions.call(() -> withoutInfo(fileId));
        if (file.isEmpty()) {
            return;
        }
        ModelInfo info = analysisOf(file.get());
        transactions.run(() -> save(info));
    }

    private Optional<StoredModelFile> withoutInfo(long fileId) {
        if (repository.findByIdOptional(fileId).isPresent()) {
            return Optional.empty();
        }
        return files.find(fileId);
    }

    /** The file may have been deleted, or analysed by someone else, while this analysis ran. */
    private void save(ModelInfo info) {
        if (withoutInfo(info.fileId).isPresent()) {
            repository.persist(info);
        }
    }

    /** The analysis as it will be stored; FAILED when the file can't be read. Touches no database. */
    private ModelInfo analysisOf(StoredModelFile file) {
        ModelInfo info = new ModelInfo();
        info.fileId = file.id();
        Path path = storage.resolve(file.storagePath());
        try {
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("File is missing on disk");
            }
            ModelAnalysis analysis = analyzer.analyze(path, file.type());
            info.status = ModelInfoStatus.ANALYZED;
            info.source = analysis.source();
            info.volumeMm3 = analysis.volumeMm3();
            info.plates = analysis.plates();
        } catch (Exception e) {
            // Anything can be wrong with an uploaded file; the request must not suffer for it.
            Log.warnf("Could not analyse file %d (%s): %s", file.id(), file.storagePath(), e.getMessage());
            info.status = ModelInfoStatus.FAILED;
        }
        return info;
    }

    /** Its own transaction: the backfill calls it from a background thread without one. */
    @Transactional
    public List<Long> fileIdsWithoutInfo() {
        return repository.fileIdsWithoutInfo();
    }

    /**
     * What is known about each of these files: the stored analysis combined with the server
     * slicing result and the fit check against the current printers. Slicing errors are only
     * included for the admin. Everything is loaded at once, however many files there are.
     */
    public Map<Long, ModelInfoDto> describe(Collection<Long> fileIds, boolean admin) {
        ModelDetailsCalculator.Context context = new ModelDetailsCalculator.Context(settings.slicing(), admin);
        Map<Long, ModelInfo> infos = repository.findByFileIds(fileIds);
        Map<Long, SliceResult> slices = slicing.find(fileIds);
        Map<Long, ModelInfoDto> described = new HashMap<>();
        for (Long fileId : fileIds) {
            ModelDetails details = calculator.calculate(infos.get(fileId), slices.get(fileId), context);
            described.put(fileId, mapper.toDto(details));
        }
        return described;
    }
}
