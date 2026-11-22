-- The warning when a request needs more of a chosen filament than Spoolman has left (#11), set by
-- the admin; NULL means the default text.
ALTER TABLE app_settings
    ADD COLUMN spoolman_not_enough_message VARCHAR(300);
