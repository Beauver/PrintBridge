-- Server slicing settings (#15).
ALTER TABLE app_settings
    ADD COLUMN slicing_enabled         BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN slicing_timeout_minutes INTEGER       NOT NULL DEFAULT 10
        CHECK (slicing_timeout_minutes BETWEEN 1 AND 60),
    -- Printer model as OrcaSlicer names it, e.g. "Bambu Lab P1S".
    ADD COLUMN slicer_printer          VARCHAR(200),
    -- Nozzle sizes the admin owns, comma-separated mm ("0.2,0.4,0.6").
    ADD COLUMN owned_nozzles           VARCHAR(100),
    ADD COLUMN default_nozzle          NUMERIC(4, 2),
    -- OrcaSlicer process/filament profile names for the default nozzle (STLs, other nozzles).
    ADD COLUMN slicer_process          VARCHAR(200),
    ADD COLUMN slicer_filament         VARCHAR(200);

-- Server slicing result per file; also the job queue (QUEUED rows, oldest first).
CREATE TABLE slice_results (
    file_id         BIGINT        PRIMARY KEY REFERENCES request_files (id) ON DELETE CASCADE,
    status          VARCHAR(16)   NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'DONE', 'FAILED')),
    -- Nozzle the numbers are for, and the one a project was made for (when that isn't owned).
    nozzle          NUMERIC(4, 2),
    made_for_nozzle NUMERIC(4, 2),
    -- Per plate: grams, seconds and filaments, or OrcaSlicer's reason for refusing it.
    plates          JSONB,
    error           VARCHAR(1000),
    queued_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    finished_at     TIMESTAMPTZ
);

CREATE INDEX slice_results_queue_idx ON slice_results (status, queued_at);

-- Plates now also store their own volume (per-plate estimates). Analyse every file again: the
-- startup backfill picks up files without model info.
DELETE FROM model_info;

-- PrusaSlicer files are now read as plain 3MF.
ALTER TABLE model_info DROP CONSTRAINT model_info_source_check;
ALTER TABLE model_info ADD CONSTRAINT model_info_source_check CHECK (source IN ('MESH', 'BAMBU'));
