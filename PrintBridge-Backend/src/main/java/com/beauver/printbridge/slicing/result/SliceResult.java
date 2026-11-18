package com.beauver.printbridge.slicing.result;

import com.beauver.printbridge.slicing.CustomSlice;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

    /** Printer model the numbers are for. */
    @Column(length = 200)
    public String printer;

    /** Nozzle the numbers are for. */
    @Column(precision = 4, scale = 2)
    public BigDecimal nozzle;

    /** The project's printer when it wasn't owned and the default printer was used instead. */
    @Column(name = "made_for_printer", length = 200)
    public String madeForPrinter;

    /** The project's nozzle when another nozzle was used (not owned, or another printer). */
    @Column(name = "made_for_nozzle", precision = 4, scale = 2)
    public BigDecimal madeForNozzle;

    /** Sliced with settings the admin chose (#21) instead of the automatic rules. */
    @Column(nullable = false)
    public boolean custom;

    /** The admin's choice, kept with the result; only set when {@link #custom}. */
    @Column(name = "chosen_printer", length = 200)
    public String chosenPrinter;

    @Column(name = "chosen_nozzle", precision = 4, scale = 2)
    public BigDecimal chosenNozzle;

    /** {@code null}: the project's own process settings. */
    @Column(name = "chosen_process", length = 200)
    public String chosenProcess;

    /** {@code null}: the project's own filaments. */
    @Column(name = "chosen_filament", length = 200)
    public String chosenFilament;

    /** The admin's choice, or empty for the automatic rules. */
    public Optional<CustomSlice> choice() {
        if (!custom) {
            return Optional.empty();
        }
        return Optional.of(new CustomSlice(chosenPrinter, chosenNozzle, chosenProcess, chosenFilament));
    }

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
