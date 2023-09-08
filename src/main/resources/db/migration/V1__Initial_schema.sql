CREATE TABLE group_status (
    id SERIAL PRIMARY KEY NOT NULL,
    status VARCHAR(100) NOT NULL UNIQUE
);

INSERT INTO group_status (status) VALUES ('ACTIVE');
INSERT INTO group_status (status) VALUES ('DISBANDED');
INSERT INTO group_status (status) VALUES ('AUTO_DISBANDED');
INSERT INTO group_status (status) VALUES ('BANNED');

CREATE TABLE member_status (
    id SERIAL PRIMARY KEY NOT NULL,
    status VARCHAR(100) NOT NULL UNIQUE
);

INSERT INTO member_status (status) VALUES ('ACTIVE');
INSERT INTO member_status (status) VALUES ('LEFT');
INSERT INTO member_status (status) VALUES ('AUTO_LEFT');
INSERT INTO member_status (status) VALUES ('KICKED');
INSERT INTO member_status (status) VALUES ('BANNED');

CREATE TABLE groups (
    id BIGSERIAL PRIMARY KEY NOT NULL,
    title VARCHAR(255) NOT NULL CHECK ( CHAR_LENGTH(title) >= 1 ),
    description VARCHAR(10000) NOT NULL CHECK ( CHAR_LENGTH(description) >= 0 ),
    max_group_size INT NOT NULL CHECK ( max_group_size >= 2 ),
    current_group_size INT NOT NULL CHECK ( current_group_size >= 0 ),
    status VARCHAR(100) NOT NULL REFERENCES group_status(status),
    last_active TIMESTAMP NOT NULL,
    created_date TIMESTAMP NOT NULL,
    last_modified_date TIMESTAMP NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    last_modified_by VARCHAR(255) NOT NULL,
    version INT NOT NULL,
    CONSTRAINT check_group_size_not_exceeding_max CHECK ( current_group_size <= max_group_size )
);

CREATE TABLE members (
    id BIGSERIAL PRIMARY KEY NOT NULL,
    username VARCHAR(255) NOT NULL,
    group_id BIGINT NOT NULL REFERENCES groups(id),
    member_status VARCHAR(100) NOT NULL REFERENCES member_status(status),
    joined_date TIMESTAMP NOT NULL,
    exited_date TIMESTAMP,
    created_date TIMESTAMP NOT NULL,
    last_modified_date TIMESTAMP NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    last_modified_by VARCHAR(255) NOT NULL,
    version INT NOT NULl,
    CONSTRAINT check_username_length CHECK ( CHAR_LENGTH(username) >= 2 ),
    CONSTRAINT check_exited_date CHECK ( exited_date > joined_date )
);

CREATE INDEX index_members_on_group_id ON members(group_id);
CREATE INDEX index_members_on_member_status ON members(member_status);