-- Spoolman integration (#11): two switches on the settings row, and the filament a requester chose
-- from Spoolman for each colour of each plate of a file (or for a whole file or link without colours).
ALTER TABLE app_settings
    ADD COLUMN spoolman_enabled         BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN spoolman_color_selection BOOLEAN NOT NULL DEFAULT FALSE,
    -- Choosing colours is part of the integration: it can't be on while the integration is off.
    ADD CONSTRAINT app_settings_spoolman_selection CHECK (spoolman_enabled OR NOT spoolman_color_selection);

CREATE TABLE filament_choices (
    id                   BIGSERIAL    PRIMARY KEY,
    file_id              BIGINT       NOT NULL REFERENCES request_files (id) ON DELETE CASCADE,
    -- NULL: the whole item (a file without colours, or a link). Otherwise the plate and the colour
    -- of that plate as the model info lists it; type and colour may each be unknown (NULL).
    plate_index          INTEGER,
    original_type        VARCHAR(100),
    original_color       VARCHAR(7),
    spoolman_filament_id BIGINT       NOT NULL,
    -- A copy of the filament when it was chosen, so the choice still reads well when Spoolman is
    -- down or the filament was deleted there.
    filament_name        VARCHAR(200),
    filament_vendor      VARCHAR(200),
    filament_material    VARCHAR(100),
    -- #RRGGBB; multi-colour filaments list their colours comma-separated instead.
    filament_color       VARCHAR(7),
    filament_colors      VARCHAR(200),
    chosen_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (plate_index IS NOT NULL OR (original_type IS NULL AND original_color IS NULL))
);

-- One choice per colour of a plate (NULLs count as equal).
CREATE UNIQUE INDEX filament_choices_slot_idx
    ON filament_choices (file_id, COALESCE(plate_index, -1), COALESCE(original_type, ''),
                         COALESCE(lower(original_color), ''));
