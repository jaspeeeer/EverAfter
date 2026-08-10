-- ---------------------------------------------------------------------
-- V17 :: Split guests.name into first_name/last_name, add optional
-- title and gender, and make party_size optional (a guest with no
-- stated party is treated as "just themselves" everywhere it's summed).
--
-- Backfill: first_name is the first whitespace-delimited token of the
-- old name; last_name is whatever remains, or null if there was only
-- one token (e.g. a mononym or a joint "Alex & Jamie" style entry).
-- This is a best-effort split — titles embedded in the old name
-- ("Mr. Smith") land in first_name and can be corrected per-row after
-- migration; the app does not depend on the split being perfect.
-- ---------------------------------------------------------------------

ALTER TABLE guests ADD COLUMN first_name varchar(100);
ALTER TABLE guests ADD COLUMN last_name  varchar(100);
ALTER TABLE guests ADD COLUMN title      varchar(20);
ALTER TABLE guests ADD COLUMN gender     varchar(20);

UPDATE guests SET
    first_name = coalesce(nullif(split_part(trim(name), ' ', 1), ''), trim(name)),
    last_name  = nullif(regexp_replace(trim(name), '^\S+\s*', ''), '');

ALTER TABLE guests ALTER COLUMN first_name SET NOT NULL;

ALTER TABLE guests DROP COLUMN name;

-- party_size: null now means "just this guest" (treated as 1 everywhere it's summed).
ALTER TABLE guests ALTER COLUMN party_size DROP DEFAULT;
ALTER TABLE guests ALTER COLUMN party_size DROP NOT NULL;
