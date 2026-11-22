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

    /** Spoolman integration switched on (#11); only has effect while {@code SPOOLMAN_URL} is set. */
    @Column(name = "spoolman_enabled", nullable = false)
    public boolean spoolmanEnabled;

    /** Requesters choose filaments from Spoolman; needs {@link #spoolmanEnabled}. */
    @Column(name = "spoolman_color_selection", nullable = false)
    public boolean spoolmanColorSelection;

    /** Colours of new projects get the closest Spoolman filament (#76); needs {@link #spoolmanColorSelection}. */
    @Column(name = "spoolman_auto_match", nullable = false)
    public boolean spoolmanAutoMatch;

    /** Largest colour difference (CIEDE2000 ΔE, 1–100) automatic matching accepts. */
    @Column(name = "spoolman_match_tolerance", nullable = false)
    public int spoolmanMatchTolerance = SpoolmanSettings.DEFAULT_MATCH_TOLERANCE;

    /** The admin's warning for a colour without a close filament; {@code null} means the default text. */
    @Column(name = "spoolman_no_match_message", length = 300)
    public String spoolmanNoMatchMessage;

    /** The admin's warning for a filament that runs short; {@code null} means the default text. */
    @Column(name = "spoolman_not_enough_message", length = 300)
    public String spoolmanNotEnoughMessage;

    /** The price estimate uses the chosen filaments' Spoolman prices (#72); needs {@link #spoolmanEnabled}. */
    @Column(name = "spoolman_prices", nullable = false)
    public boolean spoolmanPrices;

    /** Multiplier on Spoolman's price per gram. */
    @Column(name = "pricing_markup", nullable = false, precision = 5, scale = 2)
    public BigDecimal pricingMarkup;

    /** EUR per 100 g: the default price for everything without a Spoolman price; zero means none. */
    @Column(name = "price_per_100_grams", nullable = false, precision = 6, scale = 2)
    public BigDecimal pricePer100Grams;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
