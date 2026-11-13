package com.beauver.printbridge.settings;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** A printer the admin owns, with its build plate and server slicing setup. */
@Entity
@Table(name = "printers")
public class Printer extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 200)
    public String model;

    @Column(name = "build_plate_x", nullable = false)
    public int buildPlateX;

    @Column(name = "build_plate_y", nullable = false)
    public int buildPlateY;

    @Column(name = "build_plate_z", nullable = false)
    public int buildPlateZ;

    /** Nozzle sizes in mm, ascending. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "owned_nozzles", nullable = false, columnDefinition = "numeric(4,2)[]")
    public List<BigDecimal> ownedNozzles = new ArrayList<>();

    @Column(name = "default_nozzle", precision = 4, scale = 2)
    public BigDecimal defaultNozzle;

    @Column(length = 200)
    public String process;

    @Column(length = 200)
    public String filament;
}
