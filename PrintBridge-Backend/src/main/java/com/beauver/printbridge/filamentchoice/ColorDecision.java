package com.beauver.printbridge.filamentchoice;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * A colour of a plate that was decided on without a filament (#76), so automatic matching leaves it
 * alone: no filament was close enough, or someone reset an automatic match to the file's colour.
 */
@Entity
@Table(name = "color_decisions")
public class ColorDecision extends PanacheEntityBase {

    public enum Kind {
        /** No Spoolman filament of the same material was close enough; the file shows a warning. */
        NO_MATCH,
        /** Someone chose "Use file colour" on an automatic match. */
        FILE_COLOR
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "file_id", nullable = false)
    public Long fileId;

    @Column(name = "plate_index", nullable = false)
    public int plateIndex;

    @Column(name = "original_type", length = 100)
    public String originalType;

    @Column(name = "original_color", length = 7)
    public String originalColor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public Kind decision;

    @Column(name = "decided_at", nullable = false)
    public Instant decidedAt;

    /** Whether this decision is for that colour of that plate. */
    public boolean isFor(FilamentSlot slot) {
        return slot.matches(plateIndex, originalType, originalColor);
    }

    static ColorDecision of(long fileId, FilamentSlot slot, Kind kind) {
        ColorDecision decision = new ColorDecision();
        decision.fileId = fileId;
        decision.plateIndex = slot.plateIndex();
        decision.originalType = slot.type();
        decision.originalColor = slot.color();
        decision.decision = kind;
        decision.decidedAt = Instant.now();
        return decision;
    }
}
