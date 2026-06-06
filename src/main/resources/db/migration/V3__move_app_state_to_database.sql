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
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_conversation_messages_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT uk_conversation_messages_conversation_index
        UNIQUE (conversation_id, message_index)
);

CREATE INDEX ix_conversation_messages_conversation_order
    ON conversation_messages (conversation_id, message_index, created_at);
