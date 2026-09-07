CREATE TABLE intake_draft (
    id VARCHAR(40) PRIMARY KEY,
    brief CLOB NOT NULL,
    reference_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,
    result_json CLOB,
    session_id VARCHAR(100),
    message VARCHAR(500),
    attachment_name VARCHAR(160),
    attachment_type VARCHAR(30),
    event_id VARCHAR(40) UNIQUE REFERENCES event_request(id) ON DELETE SET NULL
);
