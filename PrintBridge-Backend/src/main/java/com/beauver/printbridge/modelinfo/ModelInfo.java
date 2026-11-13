package com.beauver.printbridge.modelinfo;

import com.beauver.printbridge.modelinfo.analysis.ModelSource;
import com.beauver.printbridge.modelinfo.analysis.PlateAnalysis;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/** What the analyser read from one request file. The row shares the file's id. */
@Entity
@Table(name = "model_info")
public class ModelInfo extends PanacheEntityBase {

    @Id
    @Column(name = "file_id")
    public Long fileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public ModelInfoStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    public ModelSource source;

    @Column(name = "volume_mm3")
    public Double volumeMm3;

    /** Stored as JSON: always read and written as a whole, never queried. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public List<PlateAnalysis> plates;

    @Column(name = "analyzed_at", nullable = false)
    public Instant analyzedAt;

    @PrePersist
    void onCreate() {
        if (analyzedAt == null) {
            analyzedAt = Instant.now();
        }
    }
}
