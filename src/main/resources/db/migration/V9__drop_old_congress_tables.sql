-- Drop old congress package tables that have been replaced by congress2 implementation
-- These tables are from the legacy congress package (V1, V2, V5, V6)

-- Drop parse_issue table (references periodic_transaction_report)
DROP TABLE IF EXISTS parse_issue CASCADE;

-- Drop join table (references periodic_transaction_report and ocr_parse_result)
DROP TABLE IF EXISTS periodic_transaction_report_ocr_parse_result CASCADE;

-- Drop transaction table (references periodic_transaction_report)
DROP TABLE IF EXISTS periodic_transaction_report_transaction CASCADE;

-- Drop main periodic transaction report table
DROP TABLE IF EXISTS periodic_transaction_report CASCADE;

-- Drop congress house filing table (references congress_house_filing_list)
DROP TABLE IF EXISTS congress_house_filing CASCADE;

-- Drop congress house filing list table
DROP TABLE IF EXISTS congress_house_filing_list CASCADE;
