-- Printing status between ACCEPTED and COMPLETED (#22). Existing requests keep their status.
ALTER TABLE print_requests DROP CONSTRAINT print_requests_status_check;
ALTER TABLE print_requests ADD CONSTRAINT print_requests_status_check
    CHECK (status IN ('SUBMITTED', 'IN_REVIEW', 'ACCEPTED', 'PRINTING', 'DECLINED', 'COMPLETED'));
