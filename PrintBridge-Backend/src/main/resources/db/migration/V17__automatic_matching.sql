-- Automatic filament matching (#76): when a project's colours become known, each colour nobody
-- decided on gets the closest Spoolman filament of the same material within a tolerance (CIEDE2000
-- ΔE). It needs colour selection, like the matched choices people can change afterwards.
ALTER TABLE app_settings
    ADD COLUMN spoolman_auto_match       BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN spoolman_match_tolerance  INTEGER NOT NULL DEFAULT 20
        CHECK (spoolman_match_tolerance BETWEEN 1 AND 100),
    -- NULL means the default text.
    ADD COLUMN spoolman_no_match_message VARCHAR(300),
    ADD CONSTRAINT app_settings_spoolman_auto_match CHECK (spoolman_color_selection OR NOT spoolman_auto_match);

-- Who made a choice: a person, or the automatic matching.
ALTER TABLE filament_choices
    ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'MANUAL' CHECK (source IN ('MANUAL', 'AUTO'));

-- Colours that were decided on without a filament, so matching leaves them alone: no filament was
-- close enough, or someone reset an automatic match to the file's colour.
CREATE TABLE color_decisions (
    id             BIGSERIAL    PRIMARY KEY,
    file_id        BIGINT       NOT NULL REFERENCES request_files (id) ON DELETE CASCADE,
    plate_index    INTEGER      NOT NULL,
    original_type  VARCHAR(100),
    original_color VARCHAR(7),
    decision       VARCHAR(16)  NOT NULL CHECK (decision IN ('NO_MATCH', 'FILE_COLOR')),
    decided_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX color_decisions_slot_idx
    ON color_decisions (file_id, plate_index, COALESCE(original_type, ''), COALESCE(lower(original_color), ''));
