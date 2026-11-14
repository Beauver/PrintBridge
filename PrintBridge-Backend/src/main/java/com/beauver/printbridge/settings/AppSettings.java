package com.beauver.printbridge.settings;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

/** The single row of application settings (id 1, created by the migration). */
@Entity
@Table(name = "app_settings")
public class AppSettings extends PanacheEntityBase {

    static final short ID = 1;

    @Id
    public Short id;

    @Column(name = "slicing_enabled", nullable = false)
    public boolean slicingEnabled;

    @Column(name = "slicing_timeout_minutes", nullable = false)
    public int slicingTimeoutMinutes;

    /** Used for STL files and projects made for a printer that isn't owned; {@code null} without printers. */
    @Column(name = "default_printer_id")
    public Long defaultPrinterId;

    /** Most files one request may hold; {@code null} means {@link UploadLimits#DEFAULT_MAX_FILES}. */
    @Column(name = "upload_max_files")
    public Integer uploadMaxFiles;

    /** Largest model file in MB; {@code null} means the server's ceiling ({@code UPLOAD_MAX_SIZE_MB}). */
    @Column(name = "upload_max_file_size_mb")
    public Integer uploadMaxFileSizeMb;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
