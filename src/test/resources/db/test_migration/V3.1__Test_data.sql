CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

DO $$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM groups) AND NOT EXISTS(SELECT 1 FROM members) THEN
            INSERT INTO groups (title, description, max_group_size, status, created_date, last_modified_date, created_by, last_modified_by, version)
            VALUES
                ('Group 1', 'Group 1 description', 10, 'ACTIVE', NOW(), NOW(), 'system', 'system', 1),
                ('Group 2', 'Group 2 description', 10, 'ACTIVE', NOW(), NOW(), 'system', 'system', 1),
                ('Group 3', 'Group 3 description', 10, 'ACTIVE', NOW(), NOW(), 'system', 'system', 1),
                ('Group 4', 'Group 3 description', 10, 'AUTO_DISBANDED', NOW(), NOW(), 'system', 'system', 1),
                ('Group 5', 'Group 3 description', 10, 'AUTO_DISBANDED', NOW(), NOW(), 'system', 'system', 1),
                ('Group 6', 'Group 3 description', 10, 'AUTO_DISBANDED', NOW(), NOW(), 'system', 'system', 1);

            INSERT INTO members (websocket_id, username, group_id, member_status, exited_date, created_date, last_modified_date, created_by, last_modified_by, version)
            VALUES (uuid_generate_v4(), 'user1', 1, 'ACTIVE', NULL, NOW(), NOW(), 'system', 'system', 1);
        END IF;
    END $$;

