CREATE EXTENSION "uuid-ossp";

ALTER TABLE members
DROP COLUMN joined_date;

ALTER TABLE groups
DROP COLUMN current_group_size,
DROP COLUMN last_active;

-- Update triggers and functions

-- Function to handle member join checks
-- Updates function's logic for checking group's current size against its max size
-- Removes assignment of member_status to 'ACTIVE' (this should be done by the application)
CREATE OR REPLACE FUNCTION check_group_before_member_join()
RETURNS TRIGGER AS $$
DECLARE
    member_count INT;
    group_max_size INT;
BEGIN
    -- Lock the group row for this transaction
    PERFORM * FROM groups WHERE id = NEW.group_id FOR UPDATE;

    -- Check if the group is active
    IF NOT EXISTS (
        SELECT 1 FROM groups
        WHERE id = NEW.group_id AND status = 'ACTIVE'
    ) THEN
        RAISE EXCEPTION 'Cannot save member with group because the group is not active';

    -- Check if the member is already in a group
    ELSIF EXISTS (
        SELECT 1 FROM members
        WHERE websocket_id = NEW.websocket_id AND member_status = 'ACTIVE'
    ) THEN
        RAISE EXCEPTION 'Cannot save member because the user has an active member in some group.';
    END IF;

    -- Count the number of active members in the group
    SELECT COUNT(*)
    INTO member_count
    FROM members
    WHERE group_id = NEW.group_id AND member_status = 'ACTIVE';

    -- Get the max group size
    SELECT max_group_size
    INTO group_max_size
    FROM groups
    WHERE id = NEW.group_id;

    -- Check if the number of members equals the max group size
    IF member_count >= group_max_size THEN
        RAISE EXCEPTION 'Group has reached its maximum size';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Last active time is obsolete, use updated_at instead
DROP TRIGGER trigger_update_last_active_time_for_group_when_group_updates ON groups;
DROP FUNCTION update_last_active_time_for_group_when_group_updates();

-- Group count is now derived from currently active members belonging to the group
DROP TRIGGER trigger_member_inactive_so_decrement_group_count ON members;
DROP FUNCTION member_inactive_so_decrement_group_count();


-- The best balance for read and writes is to keep a partial index on active members by group id.
-- We currently don't expect there to be a need for fast processing of non-active members

-- Drop old indexes
DROP INDEX index_members_on_group_id;
DROP INDEX index_members_on_member_status;

-- Create new partial index on active members by group id
CREATE INDEX index_active_members_on_group_id ON members (group_id) WHERE member_status = 'ACTIVE';


-- Remove exited date logic (requires rewriting of group_disbanded_so_disband_members function)
-- Can't drop function without dropping dependent triggers
DROP TRIGGER trigger_group_disbanded_so_disband_members ON groups;
DROP FUNCTION group_disbanded_so_disband_members();

-- CREATE FUNCTION group_disbanded_so_disband_members()
--     RETURNS TRIGGER AS $$
-- BEGIN
--     -- Lock the group row for this transaction
--     PERFORM * FROM groups WHERE id = NEW.id FOR UPDATE;
--
--     -- Check if the group transitioned from an active to non-active state
--     IF (
--         OLD.status = 'ACTIVE' AND NEW.status != 'ACTIVE'
--         ) THEN
--         -- Update all active members to AUTO_LEFT status
--         UPDATE members SET
--                            member_status = 'AUTO_LEFT',
--                            exited_date = NEW.last_modified_date,
--                            last_modified_date = NEW.last_modified_date,
--                            last_modified_by = NEW.last_modified_by
--         WHERE group_id = NEW.id AND member_status = 'ACTIVE';
--     END IF;
--
--     RETURN NEW;
-- END;
-- $$ LANGUAGE plpgsql;
--
-- -- Bring back trigger
-- -- Trigger to execute the function after updating group
-- CREATE TRIGGER trigger_group_disbanded_so_disband_members
--     AFTER UPDATE ON groups
--     FOR EACH ROW EXECUTE FUNCTION group_disbanded_so_disband_members();