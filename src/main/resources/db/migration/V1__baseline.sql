-- Baseline schema for the first release. Squashed from the original incremental
-- migrations; column additions are folded into their CREATE TABLE statements and
-- the data backfills (which only ever touched pre-release rows) are dropped.

CREATE TABLE app_settings (
    id INTEGER PRIMARY KEY,
    settings_json CLOB NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_app_settings_singleton CHECK (id = 1)
);

CREATE TABLE projects (
    id UUID PRIMARY KEY,
    name VARCHAR(512) NOT NULL,
    working_folder VARCHAR(2048),
    custom_instructions CLOB,
    created_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE conversations (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    name VARCHAR(512) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'idle',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_conversations_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

CREATE INDEX ix_conversations_project_order
    ON conversations (project_id, sort_order, created_at);

CREATE TABLE conversation_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    message_index INTEGER NOT NULL,
    role VARCHAR(64) NOT NULL,
    content CLOB NOT NULL,
    sources CLOB,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_conversation_messages_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT uk_conversation_messages_conversation_index
        UNIQUE (conversation_id, message_index)
);

CREATE INDEX ix_conversation_messages_conversation_order
    ON conversation_messages (conversation_id, message_index, created_at);

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

CREATE TABLE knowledge_root_scans (
    id UUID PRIMARY KEY,
    root_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'TO_DO',
    success BOOLEAN,
    message CLOB,
    total_resources BIGINT NOT NULL DEFAULT 0,
    total_size_bytes BIGINT NOT NULL DEFAULT 0,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_knowledge_root_scans_root
        FOREIGN KEY (root_id) REFERENCES knowledge_roots (id) ON DELETE CASCADE
);

CREATE INDEX ix_knowledge_root_scans_root_id
    ON knowledge_root_scans (root_id);

CREATE TABLE knowledge_resources (
    id UUID PRIMARY KEY,
    root_id UUID NOT NULL,
    resource_reference VARCHAR(2048) NOT NULL,
    display_name VARCHAR(512) NOT NULL,
    format VARCHAR(64),
    size_bytes BIGINT NOT NULL DEFAULT 0,
    last_modified_at TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    scanned_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_knowledge_resources_root
        FOREIGN KEY (root_id) REFERENCES knowledge_roots (id) ON DELETE CASCADE,
    CONSTRAINT uk_knowledge_resources_root_reference
        UNIQUE (root_id, resource_reference)
);

CREATE INDEX ix_knowledge_resources_root_id
    ON knowledge_resources (root_id);

CREATE TABLE knowledge_resource_reads (
    id UUID PRIMARY KEY,
    resource_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'TO_DO',
    success BOOLEAN,
    reason VARCHAR(64),
    message CLOB,
    read_value BLOB,
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_knowledge_resource_reads_resource
        FOREIGN KEY (resource_id) REFERENCES knowledge_resources (id) ON DELETE CASCADE,
    CONSTRAINT uk_knowledge_resource_reads_resource
        UNIQUE (resource_id)
);

CREATE TABLE knowledge_resource_conversions (
    id UUID PRIMARY KEY,
    resource_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'TO_DO',
    success BOOLEAN,
    reason VARCHAR(64),
    message CLOB,
    conversion_value CLOB,
    converted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_knowledge_resource_conversions_resource
        FOREIGN KEY (resource_id) REFERENCES knowledge_resources (id) ON DELETE CASCADE,
    CONSTRAINT uk_knowledge_resource_conversions_resource
        UNIQUE (resource_id)
);

CREATE TABLE knowledge_resource_chunks (
    id UUID PRIMARY KEY,
    resource_id UUID NOT NULL,
    chunk_index INTEGER NOT NULL,
    start_offset INTEGER NOT NULL,
    end_offset INTEGER NOT NULL,
    content_hash VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_knowledge_resource_chunks_resource
        FOREIGN KEY (resource_id) REFERENCES knowledge_resources (id) ON DELETE CASCADE,
    CONSTRAINT uk_knowledge_resource_chunks_resource_index
        UNIQUE (resource_id, chunk_index)
);

CREATE INDEX ix_knowledge_resource_chunks_resource_id
    ON knowledge_resource_chunks (resource_id);

CREATE TABLE knowledge_resource_indexes (
    id UUID PRIMARY KEY,
    chunk_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'TO_DO',
    success BOOLEAN,
    reason VARCHAR(64),
    message CLOB,
    index_reference VARCHAR(512),
    indexed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_knowledge_resource_indexes_chunk
        FOREIGN KEY (chunk_id) REFERENCES knowledge_resource_chunks (id) ON DELETE CASCADE,
    CONSTRAINT uk_knowledge_resource_indexes_chunk
        UNIQUE (chunk_id)
);
