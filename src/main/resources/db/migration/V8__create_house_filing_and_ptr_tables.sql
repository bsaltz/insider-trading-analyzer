-- Create tables for House filing lists and PTR processing

-- House filing list table (stores metadata about annual TSV files)
CREATE TABLE house_filing_list (
    id BIGSERIAL PRIMARY KEY,
    year INTEGER NOT NULL UNIQUE,
    etag VARCHAR(255),
    gcs_uri TEXT NOT NULL,
    parsed BOOLEAN NOT NULL DEFAULT FALSE,
    parsed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

-- House filing list row table (stores individual filings from TSV files)
CREATE TABLE house_filing_list_row (
    id BIGSERIAL PRIMARY KEY,
    doc_id VARCHAR(255) NOT NULL UNIQUE,
    prefix VARCHAR(50) NOT NULL,
    last VARCHAR(255) NOT NULL,
    first VARCHAR(255) NOT NULL,
    suffix VARCHAR(50) NOT NULL,
    filing_type VARCHAR(10) NOT NULL,
    state_dst VARCHAR(10) NOT NULL,
    year INTEGER NOT NULL,
    filing_date DATE NOT NULL,
    downloaded BOOLEAN NOT NULL DEFAULT FALSE,
    downloaded_at TIMESTAMP,
    raw_row_data TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    house_filing_list_id BIGINT NOT NULL REFERENCES house_filing_list(id) ON DELETE CASCADE
);

-- House PTR download table (tracks PDF downloads)
CREATE TABLE house_ptr_download (
    id BIGSERIAL PRIMARY KEY,
    doc_id VARCHAR(255) NOT NULL UNIQUE,
    gcs_uri TEXT NOT NULL,
    etag VARCHAR(255) NOT NULL,
    parsed BOOLEAN NOT NULL DEFAULT FALSE,
    parsed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

-- House PTR OCR result table (stores OCR processing results)
CREATE TABLE house_ptr_ocr_result (
    id BIGSERIAL PRIMARY KEY,
    doc_id VARCHAR(255) NOT NULL UNIQUE,
    house_ptr_download_id BIGINT NOT NULL REFERENCES house_ptr_download(id) ON DELETE CASCADE,
    gcs_uri TEXT NOT NULL
);

-- House PTR filing table (stores LLM-parsed filing data)
CREATE TABLE house_ptr_filing (
    id BIGSERIAL PRIMARY KEY,
    house_ptr_ocr_result_id BIGINT NOT NULL REFERENCES house_ptr_ocr_result(id) ON DELETE CASCADE,
    doc_id VARCHAR(255) NOT NULL,
    raw_llm_response TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- House PTR transaction table (stores individual transactions from filings)
CREATE TABLE house_ptr_transaction (
    id BIGSERIAL PRIMARY KEY,
    doc_id VARCHAR(255) NOT NULL,
    house_ptr_filing_id BIGINT NOT NULL REFERENCES house_ptr_filing(id) ON DELETE CASCADE,
    owner VARCHAR(10) NOT NULL,
    asset TEXT NOT NULL,
    transaction_type VARCHAR(10) NOT NULL,
    transaction_date VARCHAR(50) NOT NULL,
    notification_date VARCHAR(50) NOT NULL,
    amount VARCHAR(50) NOT NULL,
    certainty INTEGER NOT NULL,
    additional_data JSONB NOT NULL
);

-- Create indexes for common query patterns
CREATE INDEX idx_house_filing_list_year ON house_filing_list(year);
CREATE INDEX idx_house_filing_list_parsed ON house_filing_list(parsed);

CREATE INDEX idx_house_filing_list_row_doc_id ON house_filing_list_row(doc_id);
CREATE INDEX idx_house_filing_list_row_list_id ON house_filing_list_row(house_filing_list_id);
CREATE INDEX idx_house_filing_list_row_filing_date ON house_filing_list_row(filing_date);
CREATE INDEX idx_house_filing_list_row_downloaded ON house_filing_list_row(downloaded);
CREATE INDEX idx_house_filing_list_row_filing_type ON house_filing_list_row(filing_type);
CREATE INDEX idx_house_filing_list_row_year ON house_filing_list_row(year);
CREATE INDEX idx_house_filing_list_row_name ON house_filing_list_row(last, first);

CREATE INDEX idx_house_ptr_download_doc_id ON house_ptr_download(doc_id);
CREATE INDEX idx_house_ptr_download_parsed ON house_ptr_download(parsed);

CREATE INDEX idx_house_ptr_ocr_result_doc_id ON house_ptr_ocr_result(doc_id);
CREATE INDEX idx_house_ptr_ocr_result_download_id ON house_ptr_ocr_result(house_ptr_download_id);

CREATE INDEX idx_house_ptr_filing_doc_id ON house_ptr_filing(doc_id);
CREATE INDEX idx_house_ptr_filing_ocr_result_id ON house_ptr_filing(house_ptr_ocr_result_id);

CREATE INDEX idx_house_ptr_transaction_doc_id ON house_ptr_transaction(doc_id);
CREATE INDEX idx_house_ptr_transaction_filing_id ON house_ptr_transaction(house_ptr_filing_id);
CREATE INDEX idx_house_ptr_transaction_type ON house_ptr_transaction(transaction_type);
CREATE INDEX idx_house_ptr_transaction_asset ON house_ptr_transaction(asset);
