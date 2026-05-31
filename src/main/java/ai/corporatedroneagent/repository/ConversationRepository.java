package ai.corporatedroneagent.repository;

import ai.corporatedroneagent.config.StorageProperties;
import ai.corporatedroneagent.model.Conversation;
import ai.corporatedroneagent.util.JsonFiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Repository;

@Repository
public class ConversationRepository {

    private final JsonFiles jsonFiles;
    private final Path directory;

    public ConversationRepository(JsonFiles jsonFiles, StorageProperties storageProperties) {
        this.jsonFiles = jsonFiles;
        this.directory = storageProperties.getRoot().resolve("conversations");
    }

    public List<Conversation> findAll() {
        try {
            if (!Files.exists(directory)) {
                return List.of();
            }

            try (var stream = Files.list(directory)) {
                return stream
                        .filter(path -> path.getFileName().toString().endsWith(".json"))
                        .map(path -> jsonFiles.read(path, Conversation.class))
                        .toList();
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not list conversations", exception);
        }
    }

    public Optional<Conversation> findById(UUID id) {
        Path path = filePath(id);
        return Files.exists(path) ? Optional.of(jsonFiles.read(path, Conversation.class)) : Optional.empty();
    }

    public Conversation save(Conversation conversation) {
        jsonFiles.write(filePath(conversation.getId()), conversation);
        return conversation;
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
        try {
            return Files.deleteIfExists(filePath(id));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not delete conversation " + id, exception);
        }
    }

    private Path filePath(UUID id) {
        return directory.resolve(id + ".json");
    }
}
