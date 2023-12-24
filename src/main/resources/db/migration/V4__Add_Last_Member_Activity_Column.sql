ALTER TABLE groups
ADD COLUMN last_member_activity TIMESTAMP;

-- Precaution to prevent erroneous last_member_activity values
CREATE OR REPLACE FUNCTION check_last_member_activity() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.last_member_activity < NEW.created_date OR NEW.last_member_activity > NEW.last_modified_date THEN
        RAISE EXCEPTION 'last_member_activity:% must be after the created_date:% and before last_updated:%', NEW.last_member_activity, NEW.created_date, NEW.last_modified_date;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER check_last_member_activity_before_insert_or_update
    BEFORE INSERT OR UPDATE OF last_member_activity
    ON groups
    FOR EACH ROW
EXECUTE FUNCTION check_last_member_activity();