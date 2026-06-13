package ai.corporatedroneagent.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.corporatedroneagent.TestDatabaseSupport;
import ai.corporatedroneagent.model.Conversation;
import ai.corporatedroneagent.model.Message;
import ai.corporatedroneagent.model.Project;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

class ConversationRepositoryTests {

    private JdbcTemplate jdbcTemplate;
    private ConversationRepository conversationRepository;
    private ProjectRepository projectRepository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = TestDatabaseSupport.migratedJdbcTemplate();
        conversationRepository = new ConversationRepository(jdbcTemplate);
        projectRepository = new ProjectRepository(jdbcTemplate);
    }

    @Test
    void guardedAppendOnlyPersistsWhenExpectedUserMessageIsStillLast() {
        Project project = saveProject();
        Conversation conversation = saveConversation(project);
        Message originalUserTurn = new Message(UUID.randomUUID(), "user", "slow", null);
        Message newerUserTurn = new Message(UUID.randomUUID(), "user", "newer", null);

        conversationRepository.appendMessage(conversation.getId(), originalUserTurn);
        conversationRepository.appendMessage(conversation.getId(), newerUserTurn);

        assertThat(conversationRepository.appendMessageIfLastUserMessageIs(
                conversation.getId(),
                originalUserTurn.getId(),
                new Message(UUID.randomUUID(), "assistant", "late original", null)
        )).isEmpty();

        assertThat(conversationRepository.appendMessageIfLastUserMessageIs(
                conversation.getId(),
                newerUserTurn.getId(),
                new Message(UUID.randomUUID(), "assistant", "late newer", null)
        )).isPresent();

        assertThat(conversationRepository.findById(conversation.getId()).orElseThrow().getMessages())
                .extracting(Message::getContent)
                .containsExactly("slow", "newer", "late newer");
    }

    @Test
    void guardedAppendRejectsExpectedMessageWhenLastMessageIsNotUserAuthored() {
        Project project = saveProject();
        Conversation conversation = saveConversation(project);
        Message userTurn = new Message(UUID.randomUUID(), "user", "slow", null);
        Message assistantTurn = new Message(UUID.randomUUID(), "assistant", "already answered", null);

        conversationRepository.appendMessage(conversation.getId(), userTurn);
        conversationRepository.appendMessage(conversation.getId(), assistantTurn);

        assertThat(conversationRepository.appendMessageIfLastUserMessageIs(
                conversation.getId(),
                assistantTurn.getId(),
                new Message(UUID.randomUUID(), "assistant", "late assistant", null)
        )).isEmpty();

        assertThat(conversationRepository.findById(conversation.getId()).orElseThrow().getMessages())
                .extracting(Message::getContent)
                .containsExactly("slow", "already answered");
    }

    @Test
    void newConversationsDefaultToIdleAndUpdateStatusPersistsToReadsAndSummaries() {
        Project project = saveProject();
        Conversation conversation = saveConversation(project);

        assertThat(conversationRepository.findById(conversation.getId()).orElseThrow().getStatus())
                .isEqualTo("idle");

        assertThat(conversationRepository.updateStatus(conversation.getId(), "review")).isTrue();

        assertThat(conversationRepository.findById(conversation.getId()).orElseThrow().getStatus())
                .isEqualTo("review");
        assertThat(conversationRepository.findSummariesByProjectId(project.getId()))
                .singleElement()
                .satisfies(summary -> assertThat(summary.status()).isEqualTo("review"));
    }

    @Test
    void deleteMessageRemovesOnlyTheTargetedMessage() {
        Project project = saveProject();
        Conversation conversation = saveConversation(project);
        Message userTurn = new Message(UUID.randomUUID(), "user", "ask", null);
        Message assistantTurn = new Message(UUID.randomUUID(), "assistant", "answer", null);
        conversationRepository.appendMessage(conversation.getId(), userTurn);
        conversationRepository.appendMessage(conversation.getId(), assistantTurn);

        assertThat(conversationRepository.deleteMessage(conversation.getId(), assistantTurn.getId())).isTrue();

        assertThat(conversationRepository.findById(conversation.getId()).orElseThrow().getMessages())
                .extracting(Message::getContent)
                .containsExactly("ask");
    }

    @Test
    void deletingAMissingMessageReportsNoRowRemoved() {
        Project project = saveProject();
        Conversation conversation = saveConversation(project);

        assertThat(conversationRepository.deleteMessage(conversation.getId(), UUID.randomUUID())).isFalse();
    }

    @Test
    void appendingAfterDeletingTheLastReplyKeepsTheNewReplyAtTheEnd() {
        // The regenerate flow deletes the old reply (leaving an index gap) and then
        // appends a fresh one; nextMessageIndex is MAX + 1, so it still lands last.
        Project project = saveProject();
        Conversation conversation = saveConversation(project);
        Message userTurn = new Message(UUID.randomUUID(), "user", "ask", null);
        Message oldReply = new Message(UUID.randomUUID(), "assistant", "old answer", null);
        conversationRepository.appendMessage(conversation.getId(), userTurn);
        conversationRepository.appendMessage(conversation.getId(), oldReply);

        conversationRepository.deleteMessage(conversation.getId(), oldReply.getId());
        conversationRepository.appendMessage(
                conversation.getId(),
                new Message(UUID.randomUUID(), "assistant", "new answer", null)
        );

        assertThat(conversationRepository.findById(conversation.getId()).orElseThrow().getMessages())
                .extracting(Message::getContent)
                .containsExactly("ask", "new answer");
    }

    @Test
    void concurrentAppendsAssignUniqueSequentialIndices() throws InterruptedException {
        Project project = saveProject();
        Conversation conversation = saveConversation(project);

        int appenders = 16;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(appenders);
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        for (int index = 0; index < appenders; index++) {
            String content = "message-" + index;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    conversationRepository.appendMessage(
                            conversation.getId(),
                            new Message(UUID.randomUUID(), "user", content, null)
                    );
                } catch (Throwable failure) {
                    failures.add(failure);
                } finally {
                    done.countDown();
                }
            });
            thread.start();
        }

        start.countDown();
        done.await();

        assertThat(failures).isEmpty();
        List<Integer> indices = jdbcTemplate.queryForList(
                "SELECT message_index FROM conversation_messages WHERE conversation_id = ? ORDER BY message_index",
                Integer.class,
                conversation.getId()
        );
        assertThat(indices).hasSize(appenders).doesNotHaveDuplicates();
        assertThat(indices.get(0)).isZero();
        assertThat(indices.get(indices.size() - 1)).isEqualTo(appenders - 1);
    }

    @Test
    void duplicateMessageIndexSurfacesAsDuplicateKeyException() {
        // insertMessage's retry backstop relies on the unique-index violation
        // translating to DuplicateKeyException; guard that contract here since the
        // synchronized happy path never actually collides in-process.
        Project project = saveProject();
        Conversation conversation = saveConversation(project);
        conversationRepository.appendMessage(
                conversation.getId(),
                new Message(UUID.randomUUID(), "user", "first", null)
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO conversation_messages (
                    id, conversation_id, message_index, role, content, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                conversation.getId(),
                0,
                "user",
                "colliding index",
                Timestamp.from(Instant.now())
        )).isInstanceOf(DuplicateKeyException.class);
    }

    private Project saveProject() {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setName("Project");
        project.setWorkingFolder("");
        project.setCustomInstructions("");
        project.setConversationIds(new ArrayList<>());
        return projectRepository.save(project);
    }

    private Conversation saveConversation(Project project) {
        Conversation conversation = new Conversation();
        conversation.setId(UUID.randomUUID());
        conversation.setProjectId(project.getId());
        conversation.setName("Conversation");
        return conversationRepository.save(conversation);
    }
}
