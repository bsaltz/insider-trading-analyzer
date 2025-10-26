-- Create table for storing parsing issues (warnings and errors)
CREATE TABLE parse_issue (
    id BIGSERIAL PRIMARY KEY,
    doc_id VARCHAR(255) NOT NULL,
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('WARNING', 'ERROR')),
    category VARCHAR(50) NOT NULL CHECK (category IN ('TRANSACTION_PARSING', 'FILER_INFORMATION_PARSING', 'DOCUMENT_STRUCTURE', 'DATA_VALIDATION', 'OCR_QUALITY')),
    message TEXT NOT NULL,
    details TEXT,
    location VARCHAR(255),
    created_at TIMESTAMP NOT NULL,

    -- Foreign key reference to periodic transaction report
    CONSTRAINT fk_parse_issue_doc_id FOREIGN KEY (doc_id) REFERENCES periodic_transaction_report(doc_id) ON DELETE CASCADE
);

-- Create indexes for efficient queries
CREATE INDEX idx_parse_issue_doc_id ON parse_issue(doc_id);
CREATE INDEX idx_parse_issue_severity ON parse_issue(severity);
CREATE INDEX idx_parse_issue_category ON parse_issue(category);
CREATE INDEX idx_parse_issue_created_at ON parse_issue(created_at);
CREATE INDEX idx_parse_issue_doc_severity ON parse_issue(doc_id, severity);

-- Composite index for time-based queries
CREATE INDEX idx_parse_issue_severity_created_at ON parse_issue(severity, created_at);