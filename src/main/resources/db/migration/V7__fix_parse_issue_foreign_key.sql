-- Fix parse_issue foreign key to reference congress_house_filing instead of periodic_transaction_report
-- This makes more sense as parse issues are part of the filing -> PTR process

-- Drop the existing foreign key constraint
ALTER TABLE parse_issue DROP CONSTRAINT fk_parse_issue_doc_id;

-- Add new foreign key constraint referencing congress_house_filing
ALTER TABLE parse_issue ADD CONSTRAINT fk_parse_issue_doc_id
    FOREIGN KEY (doc_id) REFERENCES congress_house_filing(doc_id) ON DELETE CASCADE;

-- Update the comment to reflect the new reference
COMMENT ON CONSTRAINT fk_parse_issue_doc_id ON parse_issue IS 'Foreign key reference to congress_house_filing';