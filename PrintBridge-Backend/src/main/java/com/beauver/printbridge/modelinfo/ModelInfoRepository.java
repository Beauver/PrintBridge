package com.beauver.printbridge.modelinfo;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ModelInfoRepository implements PanacheRepositoryBase<ModelInfo, Long> {

    /** Files that have never been analysed (failed ones have a row and are left alone). */
    public List<Long> fileIdsWithoutInfo() {
        return getEntityManager()
                .createQuery("select f.id from RequestFile f where not exists "
                        + "(select 1 from ModelInfo m where m.fileId = f.id) order by f.id", Long.class)
                .getResultList();
    }
}
