-- When the admin marked the request as paid; NULL means not paid.
ALTER TABLE print_requests ADD COLUMN paid_at TIMESTAMPTZ;

-- Only a request with a price can be paid.
ALTER TABLE print_requests ADD CONSTRAINT print_requests_paid_needs_price
    CHECK (paid_at IS NULL OR final_price IS NOT NULL);
