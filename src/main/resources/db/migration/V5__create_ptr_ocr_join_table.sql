-- Create join table for many-to-many relationship between periodic transaction reports and OCR parse results
CREATE TABLE periodic_transaction_report_ocr_parse_result (
    id BIGSERIAL PRIMARY KEY,
    periodic_transaction_report_id BIGINT NOT NULL REFERENCES periodic_transaction_report(id) ON DELETE CASCADE,
    ocr_parse_result_id BIGINT NOT NULL REFERENCES ocr_parse_result(id) ON DELETE CASCADE,
    associated_at TIMESTAMP NOT NULL,
    confidence DOUBLE PRECISION,
    notes TEXT,

    -- Ensure unique combination of PTR and OCR result
    UNIQUE(periodic_transaction_report_id, ocr_parse_result_id)
);

-- Create indexes for efficient lookups
CREATE INDEX idx_ptr_ocr_ptr_id ON periodic_transaction_report_ocr_parse_result(periodic_transaction_report_id);
CREATE INDEX idx_ptr_ocr_ocr_id ON periodic_transaction_report_ocr_parse_result(ocr_parse_result_id);
CREATE INDEX idx_ptr_ocr_associated_at ON periodic_transaction_report_ocr_parse_result(associated_at);
CREATE INDEX idx_ptr_ocr_confidence ON periodic_transaction_report_ocr_parse_result(confidence) WHERE confidence IS NOT NULL;