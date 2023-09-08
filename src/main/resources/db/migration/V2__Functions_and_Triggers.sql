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


-- Updates members only if the group and member are active
CREATE FUNCTION check_member_and_group_before_updates()
    RETURNS TRIGGER AS $$
BEGIN
    -- Check if the member is active
    IF (OLD.member_status != 'ACTIVE')
    THEN RAISE EXCEPTION 'Cannot update member because member status is not ACTIVE';

    -- Check if the group is active
    ELSIF NOT EXISTS (
        SELECT 1 FROM groups
        WHERE id = NEW.group_id AND status = 'ACTIVE'
    ) THEN
        RAISE EXCEPTION 'Cannot update member because group status is not ACTIVE';
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