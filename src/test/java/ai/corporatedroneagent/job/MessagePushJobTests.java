package ai.corporatedroneagent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.corporatedroneagent.dto.MessageEventDto;
import ai.corporatedroneagent.model.Message;
import ai.corporatedroneagent.repository.ConversationRepository;
import ai.corporatedroneagent.service.AiChatService;
import ai.corporatedroneagent.service.ChatReply;
import ai.corporatedroneagent.service.EventService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MessagePushJobTests {

    @Test
    void errorRepliesArePublishedButNotPersistedAsAssistantMessages() {
        UUID conversationId = UUID.randomUUID();
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        EventService eventService = mock(EventService.class);
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.reply(conversationId, "hello"))
                .thenReturn(ChatReply.error("OpenAI request failed: timeout"));
        MessagePushJob job = new MessagePushJob(conversationRepository, eventService, aiChatService);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);

        try {
            job.queueAssistantReply(conversationId, "hello");

            verify(eventService, timeout(1000).times(2)).publish(eq("message-created"), eventCaptor.capture());
            assertThat(eventCaptor.getAllValues())
                    .map(MessageEventDto.class::cast)
                    .extracting(event -> event.message().role())
                    .containsExactly("status", "error");
            verify(conversationRepository, after(200).never()).appendMessage(eq(conversationId), any(Message.class));
        } finally {
            job.shutdown();
        }
    }
}
