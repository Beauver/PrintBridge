-- Several files per request, each with a number of copies (#23). Existing files keep one copy.
ALTER TABLE request_files
    ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity BETWEEN 1 AND 100);

-- Upload limits the admin sets in the app. NULL means the default: 10 files, and the server's
-- ceiling (UPLOAD_MAX_SIZE_MB) per file. The ceiling is checked by the application, since it
-- comes from the environment.
ALTER TABLE app_settings
    ADD COLUMN upload_max_files        INTEGER CHECK (upload_max_files BETWEEN 1 AND 50),
    ADD COLUMN upload_max_file_size_mb INTEGER CHECK (upload_max_file_size_mb >= 1);
