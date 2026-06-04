CREATE TABLE knowledge_roots (
    id UUID PRIMARY KEY,
    source_type VARCHAR(64) NOT NULL,
    root_reference VARCHAR(2048) NOT NULL,
    display_name VARCHAR(512) NOT NULL,
    paused BOOLEAN NOT NULL DEFAULT FALSE,
    config_json CLOB,
    total_resources BIGINT NOT NULL DEFAULT 0,
    total_size_bytes BIGINT NOT NULL DEFAULT 0,
    scan_status VARCHAR(32) NOT NULL DEFAULT 'TO_DO',
    scan_success BOOLEAN,
    scan_message CLOB,
    scan_started_at TIMESTAMP,
    scan_finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_knowledge_roots_source_reference UNIQUE (source_type, root_reference)
);
