package com.beauver.printbridge.printrequest;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PrintRequestRepository implements PanacheRepository<PrintRequest> {

    /**
     * Requests newest first, with the owner loaded. {@code ownerId == null} lists everyone's
     * requests (the admin overview).
     */
    // TODO: #5 add status/owner filters for the admin overview.
    public List<PrintRequest> listNewestFirst(Long ownerId) {
        if (ownerId == null) {
            return find("from PrintRequest r join fetch r.owner order by r.createdAt desc, r.id desc").list();
        }
        return find("from PrintRequest r join fetch r.owner where r.owner.id = ?1 order by r.createdAt desc, r.id desc",
                ownerId).list();
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
}
