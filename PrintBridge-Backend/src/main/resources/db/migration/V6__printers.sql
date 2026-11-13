-- Several owned printers (#15). Each has its own build plate and slicing setup; one is the default.
CREATE TABLE printers (
    id             BIGSERIAL     PRIMARY KEY,
    -- Printer model as OrcaSlicer names it (e.g. "Bambu Lab P1S"), or the admin's own name.
    model          VARCHAR(200)  NOT NULL,
    build_plate_x  INTEGER       NOT NULL CHECK (build_plate_x BETWEEN 1 AND 2000),
    build_plate_y  INTEGER       NOT NULL CHECK (build_plate_y BETWEEN 1 AND 2000),
    build_plate_z  INTEGER       NOT NULL CHECK (build_plate_z BETWEEN 1 AND 2000),
    -- Nozzle sizes the admin owns for this printer, comma-separated mm ("0.2,0.4,0.6").
    owned_nozzles  VARCHAR(100),
    default_nozzle NUMERIC(4, 2),
    -- OrcaSlicer process/filament profile names for the default nozzle.
    process        VARCHAR(200),
    filament       VARCHAR(200)
);

-- Owning two of the same model makes no difference for slicing or the fit check.
CREATE UNIQUE INDEX printers_model_idx ON printers (lower(model));

ALTER TABLE app_settings
    ADD COLUMN default_printer_id BIGINT REFERENCES printers (id) ON DELETE SET NULL;

-- The single printer of V4/V5 becomes the first (default) printer, when its build plate was set.
INSERT INTO printers (model, build_plate_x, build_plate_y, build_plate_z, owned_nozzles, default_nozzle, process,
                      filament)
SELECT COALESCE(slicer_printer, 'My printer'), build_plate_x, build_plate_y, build_plate_z, owned_nozzles,
       default_nozzle, slicer_process, slicer_filament
FROM app_settings
WHERE build_plate_x IS NOT NULL AND build_plate_y IS NOT NULL AND build_plate_z IS NOT NULL;

UPDATE app_settings SET default_printer_id = (SELECT min(id) FROM printers);
UPDATE app_settings SET slicing_enabled = FALSE WHERE default_printer_id IS NULL;

ALTER TABLE app_settings
    DROP COLUMN build_plate_x,
    DROP COLUMN build_plate_y,
    DROP COLUMN build_plate_z,
    DROP COLUMN slicer_printer,
    DROP COLUMN owned_nozzles,
    DROP COLUMN default_nozzle,
    DROP COLUMN slicer_process,
    DROP COLUMN slicer_filament;

-- Which printer the numbers are for, and the printer a project was made for when it isn't owned.
ALTER TABLE slice_results
    ADD COLUMN printer          VARCHAR(200),
    ADD COLUMN made_for_printer VARCHAR(200);

UPDATE slice_results SET printer = (SELECT model FROM printers ORDER BY id LIMIT 1) WHERE status = 'DONE';
