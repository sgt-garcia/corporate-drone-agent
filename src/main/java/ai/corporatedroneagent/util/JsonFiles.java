package ai.corporatedroneagent.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class JsonFiles {

    private final ObjectMapper objectMapper;

    public JsonFiles(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> T read(Path path, Class<T> type) {
        try {
            return objectMapper.readValue(path.toFile(), type);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read JSON file " + path, exception);
        }
    }

    public void write(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write JSON file " + path, exception);
        }
    }

    public <T> DocumentStore<T> documentStore(
            Path directory,
            Class<T> type,
            Function<T, UUID> idAccessor,
            String singularName,
            String pluralName) {
        return new DocumentStore<>(
                this,
                directory,
                type,
                idAccessor,
                singularName,
                pluralName);
    }

    public static final class DocumentStore<T> {

        private final JsonFiles jsonFiles;
        private final Path directory;
        private final Class<T> type;
        private final Function<T, UUID> idAccessor;
        private final String singularName;
        private final String pluralName;

        private DocumentStore(
                JsonFiles jsonFiles,
                Path directory,
                Class<T> type,
                Function<T, UUID> idAccessor,
                String singularName,
                String pluralName) {
            this.jsonFiles = jsonFiles;
            this.directory = directory;
            this.type = type;
            this.idAccessor = idAccessor;
            this.singularName = singularName;
            this.pluralName = pluralName;
        }

        public List<T> findAll() {
            try {
                if (!Files.exists(directory)) {
                    return List.of();
                }

                try (var stream = Files.list(directory)) {
                    return stream
                            .filter(path -> path.getFileName().toString().endsWith(".json"))
                            .map(path -> jsonFiles.read(path, type))
                            .toList();
                }
            } catch (IOException exception) {
                throw new UncheckedIOException("Could not list " + pluralName, exception);
            }
        }

        public Optional<T> findById(UUID id) {
            Path path = filePath(id);
            return Files.exists(path) ? Optional.of(jsonFiles.read(path, type)) : Optional.empty();
        }

        public T save(T document) {
            jsonFiles.write(filePath(idAccessor.apply(document)), document);
            return document;
        }

        public synchronized boolean delete(UUID id) {
            try {
                return Files.deleteIfExists(filePath(id));
            } catch (IOException exception) {
                throw new UncheckedIOException("Could not delete " + singularName + " " + id, exception);
            }
        }

        private Path filePath(UUID id) {
            return directory.resolve(id + ".json");
        }
    }
}
