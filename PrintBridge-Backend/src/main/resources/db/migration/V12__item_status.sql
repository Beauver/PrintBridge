-- Every file and link of a request gets its own status (#61), set by the admin; the request's
-- own status changes move them along (accept, decline, complete).
ALTER TABLE request_files
    ADD COLUMN status         VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'PRINTING', 'COMPLETED')),
    -- Optional, only kept while the item is DECLINED.
    ADD COLUMN decline_reason VARCHAR(500);

-- Existing items follow their request.
UPDATE request_files f
SET status = CASE r.status
                 WHEN 'ACCEPTED' THEN 'ACCEPTED'
                 WHEN 'PRINTING' THEN 'ACCEPTED'
                 WHEN 'COMPLETED' THEN 'COMPLETED'
                 WHEN 'DECLINED' THEN 'DECLINED'
                 ELSE 'PENDING'
    END
FROM print_requests r
WHERE r.id = f.request_id;
