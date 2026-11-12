package com.beauver.printbridge.modelinfo;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

/**
 * Analyses files uploaded before model info existed. Runs once per start in the background, so
 * the app is available right away; each file gets its own transaction.
 */
@ApplicationScoped
public class ModelInfoBackfill {

    @Inject
    ModelInfoService service;

    @ConfigProperty(name = "printbridge.model-info.backfill-on-start", defaultValue = "true")
    boolean enabled;

    void onStart(@Observes StartupEvent event) {
        if (enabled) {
            Thread.ofVirtual().name("model-info-backfill").start(this::run);
        }
    }

    /** Analyses every file without a result. A failure on one file doesn't stop the rest. */
    public int run() {
        List<Long> fileIds = service.fileIdsWithoutInfo();
        if (fileIds.isEmpty()) {
            return 0;
        }
        Log.infof("Analysing %d model file(s) without model info", fileIds.size());
        int done = 0;
        for (long fileId : fileIds) {
            try {
                service.analyzeStored(fileId);
                done++;
            } catch (RuntimeException e) {
                Log.warnf(e, "Model info backfill failed for file %d", fileId);
            }
        }
        return done;
    }
}
