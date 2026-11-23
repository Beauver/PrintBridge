-- The requester may cancel a request before it is accepted (#85): a final CANCELLED status with
-- an optional reason of its own (decline_reason stays the admin's).
ALTER TABLE print_requests DROP CONSTRAINT print_requests_status_check;
ALTER TABLE print_requests ADD CONSTRAINT print_requests_status_check
    CHECK (status IN ('SUBMITTED', 'IN_REVIEW', 'ACCEPTED', 'PRINTING', 'DECLINED', 'COMPLETED', 'CANCELLED'));
ALTER TABLE print_requests ADD COLUMN cancel_reason VARCHAR(2000);
