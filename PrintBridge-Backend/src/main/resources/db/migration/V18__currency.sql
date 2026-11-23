-- Currency of every price (#75): an ISO 4217 code. Amounts are never converted; changing it relabels them.
ALTER TABLE app_settings
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'EUR'
        CHECK (currency ~ '^[A-Z]{3}$');
