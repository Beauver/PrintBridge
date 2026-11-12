package com.beauver.printbridge.settings;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/** The single row of application settings (id 1, created by the migration). */
@Entity
@Table(name = "app_settings")
public class AppSettings extends PanacheEntityBase {

    static final short ID = 1;

    @Id
    public Short id;

    @Column(name = "build_plate_x")
    public Integer buildPlateX;

    @Column(name = "build_plate_y")
    public Integer buildPlateY;

    @Column(name = "build_plate_z")
    public Integer buildPlateZ;

    @Column(name = "slicing_enabled", nullable = false)
    public boolean slicingEnabled;

    @Column(name = "slicing_timeout_minutes", nullable = false)
    public int slicingTimeoutMinutes;

    @Column(name = "slicer_printer", length = 200)
    public String slicerPrinter;

    /** Comma-separated mm, e.g. "0.2,0.4". */
    @Column(name = "owned_nozzles", length = 100)
    public String ownedNozzles;

    @Column(name = "default_nozzle", precision = 4, scale = 2)
    public BigDecimal defaultNozzle;

    @Column(name = "slicer_process", length = 200)
    public String slicerProcess;

    @Column(name = "slicer_filament", length = 200)
    public String slicerFilament;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
