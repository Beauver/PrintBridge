-- Price estimate (#72): from Spoolman's filament prices times a markup, or else from a fixed price
-- per 100 g (0 = no automatic price). Both values are kept while the other mode is in use.
ALTER TABLE app_settings
    ADD COLUMN pricing_from_spoolman BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN pricing_markup        NUMERIC(5, 2) NOT NULL DEFAULT 1.00
        CHECK (pricing_markup BETWEEN 0.01 AND 100),
    ADD COLUMN price_per_100_grams   NUMERIC(6, 2) NOT NULL DEFAULT 0
        CHECK (price_per_100_grams BETWEEN 0 AND 1000);
