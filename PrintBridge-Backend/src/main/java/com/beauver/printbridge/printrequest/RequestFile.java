package com.beauver.printbridge.printrequest;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

/** A model file belonging to a print request. The bytes live on disk under the storage root. */
@Entity
@Table(name = "request_files")
public class RequestFile extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    public PrintRequest request;

    @Column(name = "original_filename", nullable = false, length = 255)
    public String originalFilename;

    /** Path relative to the storage root. */
    @Column(name = "storage_path", nullable = false, length = 255, unique = true)
    public String storagePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 16)
    public ModelFileType fileType;

    @Column(name = "size_bytes", nullable = false)
    public long sizeBytes;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
