-- Application-wide settings, edited by the admin. Exactly one row (id = 1).
CREATE TABLE app_settings (
    id            SMALLINT    PRIMARY KEY CHECK (id = 1),
    -- Build plate of the (single) printer in whole mm; NULL until the admin sets it.
    build_plate_x INTEGER     CHECK (build_plate_x BETWEEN 1 AND 2000),
    build_plate_y INTEGER     CHECK (build_plate_y BETWEEN 1 AND 2000),
    build_plate_z INTEGER     CHECK (build_plate_z BETWEEN 1 AND 2000),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO app_settings (id) VALUES (1);

-- What the analyser read from a model file. A missing row means "not analysed yet".
CREATE TABLE model_info (
    file_id     BIGINT           PRIMARY KEY REFERENCES request_files (id) ON DELETE CASCADE,
    status      VARCHAR(16)      NOT NULL CHECK (status IN ('ANALYZED', 'FAILED')),
    -- Where the plate data came from: MESH (STL / plain 3MF), BAMBU (Bambu Studio / Orca), PRUSA.
    source      VARCHAR(16)      CHECK (source IN ('MESH', 'BAMBU', 'PRUSA')),
    volume_mm3  DOUBLE PRECISION CHECK (volume_mm3 >= 0),
    -- Per plate: name, dimensions, sliced grams/time and filaments. Only read as a whole.
    plates      JSONB,
    analyzed_at TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CHECK (status = 'FAILED' OR (source IS NOT NULL AND volume_mm3 IS NOT NULL AND plates IS NOT NULL))
);
