-- A request can hold links (Patreon, Printables, ...) next to or instead of uploaded files (#59).
-- A link is a row in the same list, so it shares the quantity, the per-request limit and the rules
-- for adding and removing. It has no file on disk, so the file columns become optional.
ALTER TABLE request_files
    ADD COLUMN kind      VARCHAR(8) NOT NULL DEFAULT 'FILE' CHECK (kind IN ('FILE', 'LINK')),
    ADD COLUMN url       VARCHAR(2048),
    ADD COLUMN link_name VARCHAR(100),
    ALTER COLUMN original_filename DROP NOT NULL,
    ALTER COLUMN storage_path DROP NOT NULL,
    ALTER COLUMN file_type DROP NOT NULL,
    ALTER COLUMN size_bytes DROP NOT NULL;

-- A file has its file columns and no link; a link has a URL and none of the file columns.
ALTER TABLE request_files
    ADD CONSTRAINT request_files_kind_columns CHECK (
        (kind = 'FILE'
            AND original_filename IS NOT NULL AND storage_path IS NOT NULL
            AND file_type IS NOT NULL AND size_bytes IS NOT NULL
            AND url IS NULL AND link_name IS NULL)
        OR (kind = 'LINK'
            AND url IS NOT NULL
            AND original_filename IS NULL AND storage_path IS NULL
            AND file_type IS NULL AND size_bytes IS NULL)
    );
