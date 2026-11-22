-- "Use prices from Spoolman" became a Spoolman switch next to colour selection (#72): the price per
-- 100 g is now the default for everything without a Spoolman price, and the markup applies to every
-- price. Like colour selection, the switch needs the integration.
UPDATE app_settings SET pricing_from_spoolman = FALSE WHERE NOT spoolman_enabled;

ALTER TABLE app_settings RENAME COLUMN pricing_from_spoolman TO spoolman_prices;

ALTER TABLE app_settings
    ADD CONSTRAINT app_settings_spoolman_prices CHECK (spoolman_enabled OR NOT spoolman_prices);
