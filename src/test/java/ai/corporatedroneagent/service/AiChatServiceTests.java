package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.model.Conversation;
import ai.corporatedroneagent.model.Message;
import ai.corporatedroneagent.model.Project;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

class AiChatServiceTests {

    @Test
    void buildPromptMessagesUsesPersistedTranscriptAndComposedInstructions() {
        ApplicationSettings settings = new ApplicationSettings();
        settings.setCustomInstructions("Prefer concise answers.");

        Project project = new Project();
        project.setCustomInstructions("Use the repository's conventions.");

        Conversation conversation = new Conversation();
        conversation.getMessages().add(message("assistant", "Ready for work."));
        conversation.getMessages().add(message("user", "Remember alpha."));
        conversation.getMessages().add(message("assistant", "OpenAI request failed: timeout"));
        conversation.getMessages().add(message("status", "..."));
        conversation.getMessages().add(message("user", "What should you remember?"));

        List<org.springframework.ai.chat.messages.Message> promptMessages =
                AiChatService.buildPromptMessages(settings, project, conversation);

        assertThat(promptMessages)
                .extracting(Object::getClass)
                .containsExactly(
                        SystemMessage.class,
                        AssistantMessage.class,
                        UserMessage.class,
                        AssistantMessage.class,
                        UserMessage.class
                );
        assertThat(text(promptMessages.get(0)))
                .contains("Global instructions:\nPrefer concise answers.")
                .contains("Project instructions:\nUse the repository's conventions.");
        assertThat(text(promptMessages.get(1))).isEqualTo("Ready for work.");
        assertThat(text(promptMessages.get(2))).isEqualTo("Remember alpha.");
        assertThat(text(promptMessages.get(3))).isEqualTo("OpenAI request failed: timeout");
        assertThat(text(promptMessages.get(4))).isEqualTo("What should you remember?");
    }

    @Test
    void buildPromptMessagesAddsKnowledgeContextToSystemInstructions() {
        ApplicationSettings settings = new ApplicationSettings();
        Project project = new Project();
        Conversation conversation = new Conversation();
        conversation.getMessages().add(message("user", "What is the release name?"));
        KnowledgeContextSnippet snippet = new KnowledgeContextSnippet(
                "LOCAL_FOLDER",
                "Docs",
                "plans/release.txt",
                "release.txt",
                0,
                "The release name is Aurora.",
                1.25f
        );

        List<org.springframework.ai.chat.messages.Message> promptMessages =
                AiChatService.buildPromptMessages(settings, project, conversation, List.of(snippet));

        assertThat(promptMessages)
                .extracting(Object::getClass)
                .containsExactly(SystemMessage.class, UserMessage.class);
        assertThat(text(promptMessages.get(0)))
                .contains("Local knowledge:")
                .contains("[1] Docs / plans/release.txt")
                .contains("The release name is Aurora.");
    }

    private Message message(String role, String content) {
        return new Message(UUID.randomUUID(), role, content, Instant.now());
    }

    private String text(org.springframework.ai.chat.messages.Message message) {
        return ((AbstractMessage) message).getText();
    }
}
