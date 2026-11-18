package com.beauver.printbridge.printrequest.file;

import com.beauver.printbridge.modelfile.ModelFileType;
import com.beauver.printbridge.modelfile.StoredModelFile;
import com.beauver.printbridge.printrequest.PrintRequest;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * An entry of a print request: an uploaded model file, whose bytes live on disk under the storage
 * root, or a link to a model elsewhere (#59). The file columns are only set for files, the link
 * columns only for links; the database checks this.
 */
@Entity
@Table(name = "request_files")
public class RequestFile extends PanacheEntityBase {

    /** Copies of one file in a request (#23); the database checks the same range. */
    public static final int MIN_QUANTITY = 1;
    public static final int MAX_QUANTITY = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    public PrintRequest request;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    public RequestFileKind kind = RequestFileKind.FILE;

    /** Files only. */
    @Column(name = "original_filename", length = 255)
    public String originalFilename;

    /** Files only: path relative to the storage root. */
    @Column(name = "storage_path", length = 255, unique = true)
    public String storagePath;

    /** Files only. */
    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", length = 16)
    public ModelFileType fileType;

    /** Files only. */
    @Column(name = "size_bytes")
    public Long sizeBytes;

    /** Links only: an http(s) URL; the server never fetches it. */
    @Column(length = 2048)
    public String url;

    /** Links only, optional: what the requester called it. */
    @Column(name = "link_name", length = 100)
    public String linkName;

    /** How many copies of this file to print. */
    @Column(nullable = false)
    public int quantity = MIN_QUANTITY;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public boolean isLink() {
        return kind == RequestFileKind.LINK;
    }

    /** This file as the analysis and slicing code use it. Only for files, not links. */
    public StoredModelFile stored() {
        return new StoredModelFile(id, storagePath, fileType);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
