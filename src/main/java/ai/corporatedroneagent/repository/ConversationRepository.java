package ai.corporatedroneagent.repository;

import ai.corporatedroneagent.model.Conversation;
import ai.corporatedroneagent.model.Message;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ConversationRepository {

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

        replaceMessages(conversation);
        return conversation;
    }

    @Transactional
    public synchronized Optional<Conversation> update(UUID id, Consumer<Conversation> updater) {
        Optional<Conversation> conversation = findById(id);
        conversation.ifPresent(currentConversation -> {
            updater.accept(currentConversation);
            save(currentConversation);
        });
        return conversation;
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

    private void replaceMessages(Conversation conversation) {
        jdbcTemplate.update("DELETE FROM conversation_messages WHERE conversation_id = ?", conversation.getId());
        List<Message> messages = conversation.getMessages();
        for (int index = 0; index < messages.size(); index++) {
            Message message = messages.get(index);
            jdbcTemplate.update("""
                    INSERT INTO conversation_messages (
                        id, conversation_id, message_index, role, content, created_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    message.getId(),
                    conversation.getId(),
                    index,
                    message.getRole(),
                    message.getContent(),
                    timestamp(message.getCreatedAt())
            );
        }
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? Timestamp.from(Instant.now()) : Timestamp.from(instant);
    }

    private Instant instant(ResultSet resultSet, String columnName) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
