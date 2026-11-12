package com.beauver.printbridge.printrequest;

import com.beauver.printbridge.user.User;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "print_requests")
public class PrintRequest extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    public User owner;

    @Column(nullable = false, length = 100)
    public String name;

    @Column(length = 2000)
    public String description;

    /** Final price in EUR, set by the admin; {@code null} while pending. */
    @Column(name = "final_price", precision = 10, scale = 2)
    public BigDecimal finalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public RequestStatus status;

    @Column(name = "decline_reason", length = 2000)
    public String declineReason;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    public List<RequestFile> files = new ArrayList<>();

    public void addFile(RequestFile file) {
        file.request = this;
        files.add(file);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = RequestStatus.SUBMITTED;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
