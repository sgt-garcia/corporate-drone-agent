-- Conversation run status, shown as a shape/glyph/color indicator in the
-- sidebar. Stored (not derived) because the two most important states —
-- running and error — are transient at the message level and never persisted.
ALTER TABLE conversations
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'idle';

-- Backfill a sensible "lived-in" status for existing conversations from their
-- last persisted message: a trailing assistant reply reads as a settled
-- (seen) success; a trailing error turn as an error; anything else stays idle.
UPDATE conversations
SET status = 'success'
WHERE EXISTS (
    SELECT 1 FROM conversation_messages m
    WHERE m.conversation_id = conversations.id
      AND m.role = 'assistant'
      AND m.message_index = (
          SELECT MAX(m2.message_index)
          FROM conversation_messages m2
          WHERE m2.conversation_id = conversations.id
      )
);

UPDATE conversations
SET status = 'error'
WHERE EXISTS (
    SELECT 1 FROM conversation_messages m
    WHERE m.conversation_id = conversations.id
      AND m.role = 'error'
      AND m.message_index = (
          SELECT MAX(m2.message_index)
          FROM conversation_messages m2
          WHERE m2.conversation_id = conversations.id
      )
);
