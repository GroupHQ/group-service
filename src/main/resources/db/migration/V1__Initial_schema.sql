CREATE TABLE group_status (
    id SERIAL PRIMARY KEY NOT NULL,
    status VARCHAR(100) NOT NULL UNIQUE
);

INSERT INTO group_status (status) VALUES ('ACTIVE');
INSERT INTO group_status (status) VALUES ('DISBANDED');
INSERT INTO group_status (status) VALUES ('AUTO_DISBANDED');
INSERT INTO group_status (status) VALUES ('BANNED');

CREATE TABLE groups (
    id BIGSERIAL PRIMARY KEY NOT NULL,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(10000) NOT NULL,
    max_group_size INT NOT NULL,
    current_group_size INT NOT NULL,
    status VARCHAR(100) NOT NULL REFERENCES group_status(status),
    last_active TIMESTAMP NOT NULL,
    created_date TIMESTAMP NOT NULL,
    last_modified_date TIMESTAMP NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    last_modified_by VARCHAR(255) NOT NULL,
    version INT NOT NULL
);
