package com.beauver.printbridge.modelinfo;

import com.beauver.printbridge.modelfile.analysis.ModelAnalysis;
import com.beauver.printbridge.modelfile.ModelAnalyzer;
import com.beauver.printbridge.modelfile.StoredModelFile;
import com.beauver.printbridge.modelfile.StoredModelFiles;
import com.beauver.printbridge.modelinfo.dto.ModelInfoDto;
import com.beauver.printbridge.settings.SettingsService;
import com.beauver.printbridge.slicing.SlicingService;
import com.beauver.printbridge.storage.FileStorage;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
    ModelInfoMapper mapper;

    @Inject
    SettingsService settings;

    @Inject
    FileStorage storage;

    @Inject
    StoredModelFiles files;

    @Inject
    SlicingService slicing;

    /** Analyses a stored file and saves the result. Runs inside the caller's transaction. */
    @Transactional(Transactional.TxType.MANDATORY)
    public void analyze(StoredModelFile file) {
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
        repository.persist(info);
    }

    /** Analyses one stored file in its own transaction (startup backfill). */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void analyzeStored(long fileId) {
        if (repository.findByIdOptional(fileId).isEmpty()) {
            files.find(fileId).ifPresent(this::analyze);
        }
    }

    /** Its own transaction: the backfill calls it from a background thread without one. */
    @Transactional
    public List<Long> fileIdsWithoutInfo() {
        return repository.fileIdsWithoutInfo();
    }

    /**
     * The stored analysis of a file combined with its server slicing result, with the fit check
     * against the current printers. Slicing errors are only included for the admin.
     */
    public ModelInfoDto describe(long fileId, boolean admin) {
        ModelInfoMapper.Context context = new ModelInfoMapper.Context(settings.slicing(), admin);
        return mapper.toDto(repository.findById(fileId), slicing.find(fileId).orElse(null), context);
    }
}
