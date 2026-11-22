package com.beauver.printbridge.filamentchoice;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * The Spoolman filament a requester chose for one colour of one plate of a file, or for a whole
 * file or link without colours (#11). The filament's details are copied when chosen, so the choice
 * still reads well when Spoolman is down or the filament is deleted there.
 */
@Entity
@Table(name = "filament_choices")
public class FilamentChoice extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** The file or link ({@code request_files.id}); the row goes with it. */
    @Column(name = "file_id", nullable = false)
    public Long fileId;

    /** {@code null}: the whole item. */
    @Column(name = "plate_index")
    public Integer plateIndex;

    /** The colour's type as the model info lists it; {@code null} for the whole item or when unknown. */
    @Column(name = "original_type", length = 100)
    public String originalType;

    /** {@code #RRGGBB}; {@code null} for the whole item or when unknown. */
    @Column(name = "original_color", length = 7)
    public String originalColor;

    @Column(name = "spoolman_filament_id", nullable = false)
    public long spoolmanFilamentId;

    @Column(name = "filament_name", length = 200)
    public String filamentName;

    @Column(name = "filament_vendor", length = 200)
    public String filamentVendor;

    @Column(name = "filament_material", length = 100)
    public String filamentMaterial;

    /** {@code #RRGGBB}; {@code null} for multi-colour filaments or when not set. */
    @Column(name = "filament_color", length = 7)
    public String filamentColor;

    /** A multi-colour filament's colours, comma-separated; otherwise {@code null}. */
    @Column(name = "filament_colors", length = 200)
    public String filamentColors;

    @Column(name = "chosen_at", nullable = false)
    public Instant chosenAt;

    /** A person, or automatic matching (#76). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public ChoiceSource source = ChoiceSource.MANUAL;

    /** Whether this choice is for that colour of that plate. */
    public boolean isFor(FilamentSlot slot) {
        return slot.matches(plateIndex, originalType, originalColor);
    }
}
