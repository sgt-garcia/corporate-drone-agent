ALTER TABLE knowledge_resource_reads
    ADD COLUMN reason VARCHAR(64);

ALTER TABLE knowledge_resource_conversions
    ADD COLUMN reason VARCHAR(64);

ALTER TABLE knowledge_resource_indexes
    ADD COLUMN reason VARCHAR(64);

UPDATE knowledge_resource_reads
SET reason = 'UNSUPPORTED_FILE_FORMAT'
WHERE success = FALSE
  AND message = 'Unsupported file format';

UPDATE knowledge_resource_reads
SET reason = 'FILE_TOO_LARGE'
WHERE success = FALSE
  AND message = 'File is larger than 1 MB';

UPDATE knowledge_resource_reads
SET reason = 'READ_FAILED'
WHERE success = FALSE
  AND message = 'Could not read resource';

UPDATE knowledge_resource_conversions
SET reason = 'READ_DID_NOT_SUCCEED'
WHERE success = FALSE
  AND message = 'Read did not succeed';

UPDATE knowledge_resource_conversions
SET reason = 'UTF8_DECODE_FAILED'
WHERE success = FALSE
  AND message = 'Could not decode resource as UTF-8';

UPDATE knowledge_resource_indexes
SET reason = 'INDEX_FAILED'
WHERE success = FALSE;
