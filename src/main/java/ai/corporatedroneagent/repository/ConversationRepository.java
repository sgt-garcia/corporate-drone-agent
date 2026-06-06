package ai.corporatedroneagent.repository;

import ai.corporatedroneagent.config.StorageProperties;
import ai.corporatedroneagent.model.Conversation;
import ai.corporatedroneagent.util.JsonFiles;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Repository;

@Repository
public class ConversationRepository {

    private final JsonFiles.DocumentStore<Conversation> conversations;

    public ConversationRepository(JsonFiles jsonFiles, StorageProperties storageProperties) {
        this.conversations = jsonFiles.documentStore(
                storageProperties.getRoot().resolve("conversations"),
                Conversation.class,
                Conversation::getId,
                "conversation",
                "conversations");
    }

    public List<Conversation> findAll() {
        return conversations.findAll();
    }

    public Optional<Conversation> findById(UUID id) {
        return conversations.findById(id);
    }

    public Conversation save(Conversation conversation) {
        return conversations.save(conversation);
    }

    public synchronized Optional<Conversation> update(UUID id, Consumer<Conversation> updater) {
        Optional<Conversation> conversation = findById(id);
        conversation.ifPresent(currentConversation -> {
            updater.accept(currentConversation);
            save(currentConversation);
        });
        return conversation;
    }

    public synchronized boolean delete(UUID id) {
        return conversations.delete(id);
    }
}
