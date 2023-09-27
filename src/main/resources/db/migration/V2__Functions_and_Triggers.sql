-- Function to handle member join checks and update group size
CREATE FUNCTION check_group_before_member_join()
    RETURNS TRIGGER AS $$
BEGIN
    -- Lock the group row for this transaction
    PERFORM * FROM groups WHERE id = NEW.group_id FOR UPDATE;

    -- Check if the group is active
    IF NOT EXISTS (
        SELECT 1 FROM groups
        WHERE id = NEW.group_id AND status = 'ACTIVE'
    ) THEN
        RAISE EXCEPTION 'Cannot save member with group because the group is not active';

    -- Check if the group is full
    ELSIF EXISTS (
        SELECT 1 FROM groups
        WHERE id = NEW.group_id AND current_group_size = max_group_size
    ) THEN
        RAISE EXCEPTION 'Cannot save member with group because the group is full';

    -- Check if the member is already in a group
    ELSIF EXISTS (
        SELECT 1 FROM members
        WHERE websocket_id = NEW.websocket_id AND member_status = 'ACTIVE'
    ) THEN
        RAISE EXCEPTION 'Cannot save member because the user has an active member in some group.';
    END IF;

    -- Set new member status to ACTIVE and update group size
    NEW.member_status := 'ACTIVE';
    UPDATE groups SET current_group_size = current_group_size + 1 WHERE id = NEW.group_id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to execute the function before inserting into members
CREATE TRIGGER trigger_check_group_before_member_join
    BEFORE INSERT ON members
    FOR EACH ROW EXECUTE FUNCTION check_group_before_member_join();



-- Updates members only if the member is active
CREATE FUNCTION check_member_and_group_before_updates()
    RETURNS TRIGGER AS $$
BEGIN
    -- Check if the member is active
    IF (OLD.member_status != 'ACTIVE')
        THEN RAISE EXCEPTION 'Cannot update member because member status is not ACTIVE';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to execute the function before updating members
CREATE TRIGGER trigger_check_member_and_group_before_updates
    BEFORE UPDATE ON members
    FOR EACH ROW EXECUTE FUNCTION check_member_and_group_before_updates();



-- Updates last active time for group when group updates
CREATE FUNCTION update_last_active_time_for_group_when_group_updates()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.last_active := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to execute the function after creating or updating group
CREATE TRIGGER trigger_update_last_active_time_for_group_when_group_updates
    BEFORE INSERT OR UPDATE ON groups
    FOR EACH ROW EXECUTE FUNCTION update_last_active_time_for_group_when_group_updates();



-- Updates member statuses that are ACTIVE to AUTO_LEFT if group status has moved from 'ACTIVE'
CREATE FUNCTION group_disbanded_so_disband_members()
    RETURNS TRIGGER AS $$
BEGIN
    -- Lock the group row for this transaction
    PERFORM * FROM groups WHERE id = NEW.id FOR UPDATE;

    -- Check if the group transitioned from an active to non-active state
    IF (
        OLD.status = 'ACTIVE' AND NEW.status != 'ACTIVE'
        ) THEN
        -- Update all active members to AUTO_LEFT status
        UPDATE members SET member_status = 'AUTO_LEFT', exited_date = NOW()
        WHERE group_id = NEW.id AND member_status = 'ACTIVE';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to execute the function after updating group
CREATE TRIGGER trigger_group_disbanded_so_disband_members
    AFTER UPDATE ON groups
    FOR EACH ROW EXECUTE FUNCTION group_disbanded_so_disband_members();



-- Updates group's current count when a member's status changes from active
CREATE FUNCTION member_inactive_so_decrement_group_count()
    RETURNS TRIGGER AS $$
BEGIN
    -- Lock the group row for this transaction
    PERFORM * FROM groups WHERE id = NEW.group_id FOR UPDATE;
    -- Lock the member row for this transaction
    PERFORM * FROM members WHERE id = NEW.id FOR UPDATE;

    -- Check if the member transitioned from an active to non-active state
    IF (
        OLD.member_status = 'ACTIVE' AND NEW.member_status != 'ACTIVE'
        ) THEN
        -- Update group count and set member's exited time
        BEGIN
            UPDATE groups
            SET current_group_size = current_group_size - 1
            WHERE groups.id = NEW.group_id;

            NEW.exited_date := NOW();
        END;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to execute the function after updating member
CREATE TRIGGER trigger_member_inactive_so_decrement_group_count
    BEFORE UPDATE ON members
    FOR EACH ROW EXECUTE FUNCTION member_inactive_so_decrement_group_count();