package com.beauver.printbridge.slicing;

import com.beauver.printbridge.slicing.client.SliceResponse;
import com.beauver.printbridge.slicing.client.SlicerClient;
import com.beauver.printbridge.slicing.client.SlicerException;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs queued slicing jobs one at a time on a single background thread. The slicer call happens
 * outside any transaction, since it can take minutes.
 */
@ApplicationScoped
public class SliceWorker {

    @Inject
    SlicingService service;

    @Inject
    SlicerClient client;

    /** Off in tests, which call {@link #drain()} themselves. */
    @ConfigProperty(name = "printbridge.slicing.worker-autostart", defaultValue = "true")
    boolean autostart;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(Thread.ofVirtual().name("slice-worker").factory());
    private final AtomicBoolean scheduled = new AtomicBoolean();

    /** Called by Quarkus once the application has started: continue where the last run stopped. */
    @Startup
    void onStart() {
        service.requeueInterrupted();
        wake();
    }

    /** Makes sure the queue gets worked through; cheap to call often. */
    public void wake() {
        if (autostart && scheduled.compareAndSet(false, true)) {
            executor.submit(() -> {
                scheduled.set(false);
                drain();
            });
        }
    }

    /** Runs jobs until the queue is empty (or slicing is switched off); returns how many ran. */
    public int drain() {
        int count = 0;
        Optional<SliceJob> job = service.claimNext();
        while (job.isPresent()) {
            run(job.get());
            count++;
            job = service.claimNext();
        }
        return count;
    }

    private void run(SliceJob job) {
        try {
            SliceResponse response = client.slice(job.file(), job.plan(), job.timeout());
            service.complete(job.fileId(), job.plan(), response);
        } catch (SlicerException e) {
            service.fail(job.fileId(), e.getMessage());
        } catch (RuntimeException e) {
            Log.errorf(e, "Slicing file %d failed unexpectedly", job.fileId());
            service.fail(job.fileId(), "Unexpected error while slicing");
        }
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }
}
