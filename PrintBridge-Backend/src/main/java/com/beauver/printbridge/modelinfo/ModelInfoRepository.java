package com.beauver.printbridge.modelinfo;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class ModelInfoRepository implements PanacheRepositoryBase<ModelInfo, Long> {

    /** Files that have never been analysed (failed ones have a row and are left alone). */
    public List<Long> fileIdsWithoutInfo() {
        return getEntityManager()
                .createQuery("select f.id from RequestFile f where not exists "
                        + "(select 1 from ModelInfo m where m.fileId = f.id) order by f.id", Long.class)
                .getResultList();
    }

    /** The stored analyses of these files, by file id; files that aren't analysed yet are left out. */
    public Map<Long, ModelInfo> findByFileIds(Collection<Long> fileIds) {
        if (fileIds.isEmpty()) {
            return Map.of();
        }
        return list("fileId in ?1", fileIds).stream().collect(Collectors.toMap(info -> info.fileId, Function.identity()));
    }
}
