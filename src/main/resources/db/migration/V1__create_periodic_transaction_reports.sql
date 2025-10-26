-- Create tables for periodic transaction reports
CREATE TABLE periodic_transaction_report (
    id BIGSERIAL PRIMARY KEY,
    doc_id VARCHAR(255) NOT NULL UNIQUE,
    filer_full_name VARCHAR(255) NOT NULL,
    filer_status VARCHAR(50) NOT NULL,
    state VARCHAR(2) NOT NULL,
    district INTEGER NOT NULL,
    file_source_url TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE periodic_transaction_report_transaction (
    id BIGSERIAL PRIMARY KEY,
    periodic_transaction_report_id BIGINT NOT NULL REFERENCES periodic_transaction_report(id),
    owner VARCHAR(10),
    asset_name TEXT NOT NULL,
    asset_type VARCHAR(10) NOT NULL,
    filing_status VARCHAR(50) NOT NULL,
    trade_type VARCHAR(50) NOT NULL,
    amount_range VARCHAR(50) NOT NULL,
    trade_date DATE NOT NULL,
    file_source_url TEXT NOT NULL,
    parsed_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for common queries
CREATE INDEX idx_periodic_transaction_report_doc_id ON periodic_transaction_report(doc_id);
CREATE INDEX idx_periodic_transaction_report_filer ON periodic_transaction_report(filer_full_name);
CREATE INDEX idx_periodic_transaction_report_state_district ON periodic_transaction_report(state, district);
CREATE INDEX idx_transaction_report_id ON periodic_transaction_report_transaction(periodic_transaction_report_id);
CREATE INDEX idx_transaction_asset ON periodic_transaction_report_transaction(asset_name);
CREATE INDEX idx_transaction_trade_date ON periodic_transaction_report_transaction(trade_date);
CREATE INDEX idx_transaction_trade_type ON periodic_transaction_report_transaction(trade_type);