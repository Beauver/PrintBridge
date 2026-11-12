package com.beauver.printbridge.slicing;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class SliceResultRepository implements PanacheRepositoryBase<SliceResult, Long> {

    /** The job that has waited longest. */
    public Optional<SliceResult> nextQueued() {
        return find("status", Sort.by("queuedAt").and("fileId"), SliceStatus.QUEUED).firstResultOptional();
    }

    public long deleteQueued() {
        return delete("status", SliceStatus.QUEUED);
    }

    /** Jobs that were running when the backend stopped go back into the queue. */
    public int requeueRunning() {
        return update("status = ?1 where status = ?2", SliceStatus.QUEUED, SliceStatus.RUNNING);
    }
}
