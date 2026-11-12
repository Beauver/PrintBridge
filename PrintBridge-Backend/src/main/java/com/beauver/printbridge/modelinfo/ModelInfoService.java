package com.beauver.printbridge.modelinfo;

import com.beauver.printbridge.modelinfo.analysis.ModelAnalysis;
import com.beauver.printbridge.modelinfo.analysis.ModelAnalyzer;
import com.beauver.printbridge.modelinfo.dto.ModelInfoDto;
import com.beauver.printbridge.printrequest.RequestFile;
import com.beauver.printbridge.settings.SettingsService;
import com.beauver.printbridge.storage.FileStorage;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
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
    EntityManager entityManager;

    /** Analyses a stored file and saves the result. Runs inside the caller's transaction. */
    @Transactional(Transactional.TxType.MANDATORY)
    public void analyze(RequestFile file) {
        ModelInfo info = new ModelInfo();
        info.fileId = file.id;
        Path path = storage.resolve(file.storagePath);
        try {
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("File is missing on disk");
            }
            ModelAnalysis analysis = analyzer.analyze(path, file.fileType);
            info.status = ModelInfoStatus.ANALYZED;
            info.source = analysis.source();
            info.volumeMm3 = analysis.volumeMm3();
            info.plates = analysis.plates();
        } catch (Exception e) {
            // Anything can be wrong with an uploaded file; the request must not suffer for it.
            Log.warnf("Could not analyse file %d (%s): %s", file.id, file.storagePath, e.getMessage());
            info.status = ModelInfoStatus.FAILED;
        }
        repository.persist(info);
    }

    /** Analyses one stored file in its own transaction (startup backfill). */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void analyzeStored(long fileId) {
        RequestFile file = entityManager.find(RequestFile.class, fileId);
        if (file != null && repository.findByIdOptional(fileId).isEmpty()) {
            analyze(file);
        }
    }

    public List<Long> fileIdsWithoutInfo() {
        return repository.fileIdsWithoutInfo();
    }

    /** The stored analysis of a file, with the fit check against the current build plate. */
    public ModelInfoDto describe(RequestFile file) {
        return mapper.toDto(repository.findById(file.id), settings.buildPlate());
    }
}
