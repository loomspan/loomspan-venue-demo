CREATE TABLE room_credit (
 proposal_id VARCHAR(40) PRIMARY KEY REFERENCES proposal(id) ON DELETE CASCADE,
 amount_cents INTEGER NOT NULL CHECK(amount_cents=10000),
 approved_by VARCHAR(100) NOT NULL, approved_at TIMESTAMP NOT NULL
);
