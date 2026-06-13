package ai.corporatedroneagent.repository;

import ai.corporatedroneagent.dto.ConversationSummaryDto;
import ai.corporatedroneagent.model.Conversation;
import ai.corporatedroneagent.model.Message;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ConversationRepository {

    // A conversation's message_index is assigned as MAX(message_index) + 1. Two
    // appends on different threads (e.g. a user message and an assistant reply)
    // can read the same MAX before either row is visible to the other, colliding
    // on uk_conversation_messages_conversation_index. We retry the rare conflict
    // with a freshly recomputed index rather than surfacing it to the caller.
    private static final int MAX_APPEND_ATTEMPTS = 8;

    private final JdbcTemplate jdbcTemplate;

    public ConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Conversation> findAll() {
        return jdbcTemplate.query("""
                SELECT *
                FROM conversations
                ORDER BY created_at, name
                """, this::mapConversation);
    }

    public Optional<Conversation> findById(UUID id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM conversations WHERE id = ?",
                    this::mapConversation,
                    id
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public List<ConversationSummaryDto> findSummariesByProjectId(UUID projectId) {
        return jdbcTemplate.query("""
                SELECT id, project_id, name
                FROM conversations
                WHERE project_id = ?
                ORDER BY sort_order, created_at, name
                """, this::mapSummary, projectId);
    }

    @Transactional
    public Conversation save(Conversation conversation) {
        Instant now = Instant.now();
        if (conversation.getId() == null) {
            conversation.setId(UUID.randomUUID());
        }

        if (findById(conversation.getId()).isEmpty()) {
            jdbcTemplate.update("""
                    INSERT INTO conversations (
                        id, project_id, name, sort_order, created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    conversation.getId(),
                    conversation.getProjectId(),
                    conversation.getName(),
                    nextSortOrder(conversation.getProjectId()),
                    Timestamp.from(now),
                    Timestamp.from(now)
            );
        } else {
            jdbcTemplate.update("""
                    UPDATE conversations
                    SET project_id = ?,
                        name = ?,
                        updated_at = ?
                    WHERE id = ?
                    """,
                    conversation.getProjectId(),
                    conversation.getName(),
                    Timestamp.from(now),
                    conversation.getId()
            );
        }

        return conversation;
    }

    // Deliberately not @Transactional: the message INSERT must commit before the
    // synchronized monitor is released, otherwise a concurrent appender computing
    // MAX(message_index) in its own transaction would not see this row yet and
    // would pick a colliding index. Running each statement in autocommit closes
    // that window; insertMessage additionally retries on the unique-constraint
    // conflict as a backstop.
    public synchronized Optional<Message> appendMessage(UUID conversationId, Message message) {
        if (!exists(conversationId)) {
            return Optional.empty();
        }
        prepareForAppend(message);
        insertMessage(conversationId, message);
        return Optional.of(message);
    }

    public synchronized Optional<Message> appendMessageIfLastUserMessageIs(
            UUID conversationId,
            UUID expectedLastUserMessageId,
            Message message
    ) {
        if (expectedLastUserMessageId == null || !exists(conversationId)) {
            return Optional.empty();
        }
        if (!lastMessageMatches(conversationId, expectedLastUserMessageId, "user")) {
            return Optional.empty();
        }
        prepareForAppend(message);
        insertMessage(conversationId, message);
        return Optional.of(message);
    }

    // Delete a single message by id. Used to drop the persisted assistant reply
    // before a regenerate re-queues a fresh one, so the turn is replaced rather
    // than duplicated. Leaving a gap in message_index is harmless — nextMessageIndex
    // is MAX + 1, so the regenerated reply still lands at the end.
    @Transactional
    public synchronized boolean deleteMessage(UUID conversationId, UUID messageId) {
        int deleted = jdbcTemplate.update(
                "DELETE FROM conversation_messages WHERE conversation_id = ? AND id = ?",
                conversationId,
                messageId
        );
        if (deleted > 0) {
            touchConversation(conversationId);
        }
        return deleted > 0;
    }

    private void prepareForAppend(Message message) {
        if (message.getId() == null) {
            message.setId(UUID.randomUUID());
        }
        if (message.getCreatedAt() == null) {
            message.setCreatedAt(Instant.now());
        }
    }

    private void insertMessage(UUID conversationId, Message message) {
        DuplicateKeyException lastConflict = null;
        for (int attempt = 0; attempt < MAX_APPEND_ATTEMPTS; attempt++) {
            try {
                jdbcTemplate.update("""
                        INSERT INTO conversation_messages (
                            id, conversation_id, message_index, role, content, created_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                        message.getId(),
                        conversationId,
                        nextMessageIndex(conversationId),
                        message.getRole(),
                        message.getContent(),
                        timestamp(message.getCreatedAt())
                );
                touchConversation(conversationId);
                return;
            } catch (DuplicateKeyException conflict) {
                lastConflict = conflict;
            }
        }
        throw lastConflict;
    }

    private void touchConversation(UUID conversationId) {
        jdbcTemplate.update(
                "UPDATE conversations SET updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()),
                conversationId
        );
    }

    @Transactional
    public synchronized boolean delete(UUID id) {
        return jdbcTemplate.update("DELETE FROM conversations WHERE id = ?", id) > 0;
    }

    private Conversation mapConversation(ResultSet resultSet, int rowNumber) throws SQLException {
        Conversation conversation = new Conversation();
        UUID conversationId = resultSet.getObject("id", UUID.class);
        conversation.setId(conversationId);
        conversation.setProjectId(resultSet.getObject("project_id", UUID.class));
        conversation.setName(resultSet.getString("name"));
        conversation.setMessages(messages(conversationId));
        return conversation;
    }

    private ConversationSummaryDto mapSummary(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ConversationSummaryDto(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("project_id", UUID.class),
                resultSet.getString("name")
        );
    }

    private List<Message> messages(UUID conversationId) {
        return jdbcTemplate.query("""
                SELECT *
                FROM conversation_messages
                WHERE conversation_id = ?
                ORDER BY message_index, created_at
                """, this::mapMessage, conversationId);
    }

    private Message mapMessage(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Message(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("role"),
                resultSet.getString("content"),
                instant(resultSet, "created_at")
        );
    }

    private int nextSortOrder(UUID projectId) {
        Integer next = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(sort_order) + 1, 0)
                FROM conversations
                WHERE project_id = ?
                """, Integer.class, projectId);
        return next == null ? 0 : next;
    }

    private int nextMessageIndex(UUID conversationId) {
        Integer next = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(message_index) + 1, 0)
                FROM conversation_messages
                WHERE conversation_id = ?
                """, Integer.class, conversationId);
        return next == null ? 0 : next;
    }

    private boolean lastMessageMatches(UUID conversationId, UUID expectedMessageId, String expectedRole) {
        try {
            Boolean matches = jdbcTemplate.queryForObject("""
                    SELECT id, role
                    FROM conversation_messages
                    WHERE conversation_id = ?
                    ORDER BY message_index DESC, created_at DESC
                    LIMIT 1
                    """,
                    (resultSet, rowNumber) -> expectedMessageId.equals(resultSet.getObject("id", UUID.class))
                            && expectedRole.equals(resultSet.getString("role")),
                    conversationId
            );
            return Boolean.TRUE.equals(matches);
        } catch (EmptyResultDataAccessException exception) {
            return false;
        }
    }

    private boolean exists(UUID conversationId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM conversations WHERE id = ?",
                Integer.class,
                conversationId
        );
        return count != null && count > 0;
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? Timestamp.from(Instant.now()) : Timestamp.from(instant);
    }

    private Instant instant(ResultSet resultSet, String columnName) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
