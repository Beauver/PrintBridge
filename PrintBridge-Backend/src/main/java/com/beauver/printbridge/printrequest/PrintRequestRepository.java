package com.beauver.printbridge.printrequest;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
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
