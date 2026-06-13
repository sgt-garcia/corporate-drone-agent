package ai.corporatedroneagent.repository;

import static org.assertj.core.api.Assertions.assertThat;

import ai.corporatedroneagent.TestDatabaseSupport;
import ai.corporatedroneagent.model.Conversation;
import ai.corporatedroneagent.model.Message;
import ai.corporatedroneagent.model.Project;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ConversationRepositoryTests {

    private ConversationRepository conversationRepository;
    private ProjectRepository projectRepository;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = TestDatabaseSupport.migratedJdbcTemplate();
        conversationRepository = new ConversationRepository(jdbcTemplate);
        projectRepository = new ProjectRepository(jdbcTemplate);
    }

    @Test
    void guardedAppendOnlyPersistsWhenExpectedMessageIsStillLast() {
        Project project = saveProject();
        Conversation conversation = saveConversation(project);
        Message originalUserTurn = new Message(UUID.randomUUID(), "user", "slow", null);
        Message newerUserTurn = new Message(UUID.randomUUID(), "user", "newer", null);

        conversationRepository.appendMessage(conversation.getId(), originalUserTurn);
        conversationRepository.appendMessage(conversation.getId(), newerUserTurn);

        assertThat(conversationRepository.appendMessageIfLastMessageIs(
                conversation.getId(),
                originalUserTurn.getId(),
                new Message(UUID.randomUUID(), "assistant", "late original", null)
        )).isEmpty();

        assertThat(conversationRepository.appendMessageIfLastMessageIs(
                conversation.getId(),
                newerUserTurn.getId(),
                new Message(UUID.randomUUID(), "assistant", "late newer", null)
        )).isPresent();

        assertThat(conversationRepository.findById(conversation.getId()).orElseThrow().getMessages())
                .extracting(Message::getContent)
                .containsExactly("slow", "newer", "late newer");
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
