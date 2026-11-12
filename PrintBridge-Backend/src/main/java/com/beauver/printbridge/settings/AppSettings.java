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

    @Column(name = "build_plate_x")
    public Integer buildPlateX;

    @Column(name = "build_plate_y")
    public Integer buildPlateY;

    @Column(name = "build_plate_z")
    public Integer buildPlateZ;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
