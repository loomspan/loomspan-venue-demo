CREATE TABLE room (
 id VARCHAR(40) PRIMARY KEY, name VARCHAR(100) NOT NULL,
 capacity INTEGER NOT NULL CHECK (capacity > 0), price_cents INTEGER NOT NULL CHECK (price_cents >= 0),
 version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE event_request (
 id VARCHAR(40) PRIMARY KEY, title VARCHAR(120) NOT NULL, event_date DATE NOT NULL,
 attendees INTEGER NOT NULL CHECK (attendees > 0), budget_cents INTEGER NOT NULL CHECK (budget_cents > 0),
 created_at TIMESTAMP NOT NULL
);
CREATE TABLE assessment (
 id VARCHAR(40) PRIMARY KEY, event_id VARCHAR(40) NOT NULL REFERENCES event_request(id),
 status VARCHAR(30) NOT NULL, summary VARCHAR(4000) NOT NULL, questions VARCHAR(4000) NOT NULL,
 session_id VARCHAR(100), created_at TIMESTAMP NOT NULL
);
CREATE TABLE proposal (
 id VARCHAR(40) PRIMARY KEY, assessment_id VARCHAR(40) NOT NULL UNIQUE REFERENCES assessment(id),
 room_id VARCHAR(40) NOT NULL REFERENCES room(id), room_version BIGINT NOT NULL,
 total_cents INTEGER NOT NULL CHECK (total_cents >= 0)
);
CREATE TABLE booking (
 id VARCHAR(40) PRIMARY KEY, proposal_id VARCHAR(40) UNIQUE REFERENCES proposal(id),
 room_id VARCHAR(40) NOT NULL REFERENCES room(id), title VARCHAR(120) NOT NULL,
 starts_at TIMESTAMP NOT NULL, ends_at TIMESTAMP NOT NULL, total_cents INTEGER NOT NULL,
 CHECK (ends_at > starts_at)
);
CREATE INDEX booking_room_interval ON booking(room_id, starts_at, ends_at);
