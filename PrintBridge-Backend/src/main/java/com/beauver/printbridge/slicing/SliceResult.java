package com.beauver.printbridge.slicing;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Server slicing of one request file; QUEUED rows are the job queue. The row shares the file's id. */
@Entity
@Table(name = "slice_results")
public class SliceResult extends PanacheEntityBase {

    @Id
    @Column(name = "file_id")
    public Long fileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public SliceStatus status;

    /** Nozzle the numbers are for. */
    @Column(precision = 4, scale = 2)
    public BigDecimal nozzle;

    /** The project's nozzle when it wasn't owned and the default nozzle was used instead. */
    @Column(name = "made_for_nozzle", precision = 4, scale = 2)
    public BigDecimal madeForNozzle;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public List<SlicedPlate> plates;

    @Column(length = 1000)
    public String error;

    @Column(name = "queued_at", nullable = false)
    public Instant queuedAt;

    @Column(name = "finished_at")
    public Instant finishedAt;
}
