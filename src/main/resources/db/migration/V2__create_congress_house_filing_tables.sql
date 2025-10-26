-- Create tables for Congress House filing lists and individual filings
CREATE TABLE congress_house_filing_list (
    id BIGSERIAL PRIMARY KEY,
    year INTEGER NOT NULL UNIQUE,
    etag VARCHAR(255) NOT NULL
);

CREATE TABLE congress_house_filing (
    id BIGSERIAL PRIMARY KEY,
    congress_house_filing_list_id BIGINT NOT NULL REFERENCES congress_house_filing_list(id),
    doc_id VARCHAR(255) NOT NULL UNIQUE,
    prefix VARCHAR(50) NOT NULL,
    last VARCHAR(255) NOT NULL,
    first VARCHAR(255) NOT NULL,
    suffix VARCHAR(50) NOT NULL,
    filing_type VARCHAR(10) NOT NULL,
    state_dst VARCHAR(10) NOT NULL,
    year INTEGER NOT NULL,
    filing_date DATE NOT NULL,
    etag VARCHAR(255)
);

-- Create indexes for common queries
CREATE INDEX idx_congress_house_filing_list_year ON congress_house_filing_list(year);
CREATE INDEX idx_congress_house_filing_doc_id ON congress_house_filing(doc_id);
CREATE INDEX idx_congress_house_filing_list_id ON congress_house_filing(congress_house_filing_list_id);
CREATE INDEX idx_congress_house_filing_date ON congress_house_filing(filing_date);
CREATE INDEX idx_congress_house_filing_name ON congress_house_filing(last, first);