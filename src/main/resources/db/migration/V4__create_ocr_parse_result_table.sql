-- Create table for OCR parse results
CREATE TABLE ocr_parse_result (
    id BIGSERIAL PRIMARY KEY,
    response TEXT NOT NULL,
    created_time TIMESTAMP NOT NULL
);

-- Create index for common queries
CREATE INDEX idx_ocr_parse_result_created_time ON ocr_parse_result(created_time);