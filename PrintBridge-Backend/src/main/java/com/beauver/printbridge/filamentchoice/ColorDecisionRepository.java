package com.beauver.printbridge.filamentchoice;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.List;

@ApplicationScoped
public class ColorDecisionRepository implements PanacheRepositoryBase<ColorDecision, Long> {

    public List<ColorDecision> listByFileIds(Collection<Long> fileIds) {
        if (fileIds.isEmpty()) {
            return List.of();
        }
        return list("fileId in ?1 order by id", fileIds);
    }

    public List<ColorDecision> listByFileId(long fileId) {
        return list("fileId = ?1 order by id", fileId);
    }
}
