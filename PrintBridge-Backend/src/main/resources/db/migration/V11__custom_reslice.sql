-- The admin can re-slice one file with a chosen printer, nozzle and profiles (#21). The choice is
-- stored when the job is queued, so it survives a restart, and stays with the result so the card
-- can show it and the next custom re-slice can start from it. A plain re-slice clears it.
ALTER TABLE slice_results
    ADD COLUMN custom          BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN chosen_printer  VARCHAR(200),
    ADD COLUMN chosen_nozzle   NUMERIC(4, 2),
    -- NULL with custom = TRUE: the project's own settings / filaments.
    ADD COLUMN chosen_process  VARCHAR(200),
    ADD COLUMN chosen_filament VARCHAR(200);

ALTER TABLE slice_results
    ADD CONSTRAINT slice_results_custom_choice CHECK (
        custom = (chosen_printer IS NOT NULL AND chosen_nozzle IS NOT NULL)
    );
