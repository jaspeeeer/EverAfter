-- ---------------------------------------------------------------------
-- Admin-managed flag on guest_roles controlling whether a role shows up
-- in the "import from guests" picker on the Entourage settings card. The
-- eight canonical wedding-party roles seeded in V12 are marked eligible;
-- everything else (Parents, Officiating Pastor, Bearer, Guest) defaults
-- to false and can be toggled by an admin later.
-- ---------------------------------------------------------------------

ALTER TABLE guest_roles ADD COLUMN entourage_eligible boolean NOT NULL DEFAULT false;

UPDATE guest_roles
   SET entourage_eligible = true
 WHERE slug IN (
    'PRINCIPAL_SPONSOR',
    'SECONDARY_SPONSOR',
    'BEST_MAN',
    'MAID_OF_HONOR',
    'BRIDESMAID',
    'GROOMSMAN',
    'RING_BEARER',
    'FLOWER_GIRL'
 );
