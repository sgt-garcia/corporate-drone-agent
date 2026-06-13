-- Per-message provenance: the knowledge the agent consulted for an assistant
-- reply, stored as JSON so it survives reload and renders in the sources panel.
-- Nullable — only assistant replies with retrieved context carry sources.
ALTER TABLE conversation_messages
    ADD COLUMN sources CLOB;
