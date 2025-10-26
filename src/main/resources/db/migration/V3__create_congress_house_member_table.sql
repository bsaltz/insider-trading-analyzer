-- Create table for Congress House members
CREATE TABLE congress_house_member (
    id BIGSERIAL PRIMARY KEY,
    prefix VARCHAR(50) NOT NULL,
    last VARCHAR(255) NOT NULL,
    first VARCHAR(255) NOT NULL,
    suffix VARCHAR(50) NOT NULL,
    state VARCHAR(2) NOT NULL,
    district INTEGER NOT NULL
);

-- Create indexes for common queries
CREATE INDEX idx_congress_house_member_name ON congress_house_member(last, first);
CREATE INDEX idx_congress_house_member_state_district ON congress_house_member(state, district);