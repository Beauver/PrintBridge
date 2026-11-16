-- When a refresh token was replaced by a new one (rotation). Tells "used, a successor exists" apart
-- from "revoked by logout or password reset": only the first may be presented again for a few
-- seconds, which happens when two browser tabs refresh with the same cookie at once.
ALTER TABLE refresh_tokens ADD COLUMN rotated_at TIMESTAMPTZ;
