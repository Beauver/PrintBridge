package com.beauver.printbridge.printrequest;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import com.beauver.printbridge.printrequest.file.ItemStatus;
import com.beauver.printbridge.printrequest.file.RequestFile;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class PrintRequestRepository implements PanacheRepository<PrintRequest> {

    /** One owner's requests, newest first, with the owner loaded. */
    public List<PrintRequest> listOwnNewestFirst(long ownerId) {
        return find("from PrintRequest r join fetch r.owner where r.owner.id = ?1 order by r.createdAt desc, r.id desc",
                ownerId).list();
    }

    /**
     * Everyone's requests in the given statuses, newest first, with the owner loaded.
     * {@code search} (optional) matches the request name or the owner's username, ignoring case.
     */
    public List<PrintRequest> searchNewestFirst(Collection<RequestStatus> statuses, String search) {
        if (search == null || search.isBlank()) {
            return find("from PrintRequest r join fetch r.owner where r.status in ?1 order by r.createdAt desc, r.id desc",
                    statuses).list();
        }
        String pattern = "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
        return find("from PrintRequest r join fetch r.owner where r.status in ?1"
                        + " and (lower(r.name) like ?2 escape '\\' or lower(r.owner.username) like ?2 escape '\\')"
                        + " order by r.createdAt desc, r.id desc",
                statuses, pattern).list();
    }

    /** One request with owner and files loaded. */
    public Optional<PrintRequest> findWithDetails(long id) {
        return find("from PrintRequest r join fetch r.owner left join fetch r.files where r.id = ?1", id)
                .firstResultOptional();
    }

    /**
     * Like {@link #findWithDetails}, with the request's row locked until the transaction ends, so
     * two uploads at once can't both pass the file limit. Locked on its own: PostgreSQL can't lock
     * through the outer join on the files.
     */
    public Optional<PrintRequest> findLockedWithDetails(long id) {
        if (findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE).isEmpty()) {
            return Optional.empty();
        }
        return findWithDetails(id);
    }

    /**
     * Completed and not-declined items per request (#61), for the lists; requests without items
     * are left out. One query for the whole list.
     */
    public Map<Long, ItemProgress> itemProgress(Collection<Long> requestIds) {
        if (requestIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = getEntityManager()
                .createQuery("select f.request.id,"
                        + " sum(case when f.status = :completed then 1 else 0 end),"
                        + " sum(case when f.status <> :declined then 1 else 0 end)"
                        + " from RequestFile f where f.request.id in :ids group by f.request.id", Object[].class)
                .setParameter("completed", ItemStatus.COMPLETED)
                .setParameter("declined", ItemStatus.DECLINED)
                .setParameter("ids", requestIds)
                .getResultList();
        Map<Long, ItemProgress> progress = new HashMap<>();
        for (Object[] row : rows) {
            progress.put((Long) row[0], new ItemProgress(((Number) row[1]).intValue(), ((Number) row[2]).intValue()));
        }
        return progress;
    }

    /** The files and links of these requests, each request's in upload order, with the request loaded. */
    public List<RequestFile> filesOf(Collection<Long> requestIds) {
        if (requestIds.isEmpty()) {
            return List.of();
        }
        return getEntityManager()
                .createQuery("select f from RequestFile f join fetch f.request where f.request.id in :ids"
                        + " order by f.request.id, f.id", RequestFile.class)
                .setParameter("ids", requestIds)
                .getResultList();
    }

    public List<Long> idsOfOwner(long ownerId) {
        return getEntityManager()
                .createQuery("select r.id from PrintRequest r where r.owner.id = :owner", Long.class)
                .setParameter("owner", ownerId)
                .getResultList();
    }

    /** Makes % and _ in user input match literally. */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
