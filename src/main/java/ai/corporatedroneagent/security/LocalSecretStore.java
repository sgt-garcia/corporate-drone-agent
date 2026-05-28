package ai.corporatedroneagent.security;

import ai.corporatedroneagent.config.StorageProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class LocalSecretStore implements SecretStore {

    private static final TypeReference<Map<String, StoredSecret>> SECRET_MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final Path path;
    private final SecretProtector secretProtector;

    public LocalSecretStore(ObjectMapper objectMapper, StorageProperties storageProperties) {
        this.objectMapper = objectMapper;
        this.path = storageProperties.getRoot().resolve("secrets.json");
        this.secretProtector = SecretProtector.create();
    }

    @Override
    public synchronized Optional<String> get(String key) {
        StoredSecret secret = readSecrets().get(key);
        if (secret == null) {
            return Optional.empty();
        }

        return Optional.of(secretProtector.unprotect(secret));
    }

    @Override
    public synchronized void put(String key, String secret) {
        Map<String, StoredSecret> secrets = readSecrets();
        secrets.put(key, secretProtector.protect(secret));
        writeSecrets(secrets);
    }

    @Override
    public synchronized void delete(String key) {
        Map<String, StoredSecret> secrets = readSecrets();
        if (secrets.remove(key) != null) {
            writeSecrets(secrets);
        }
    }

    private Map<String, StoredSecret> readSecrets() {
        if (!Files.exists(path)) {
            return new HashMap<>();
        }

        try {
            return new HashMap<>(objectMapper.readValue(path.toFile(), SECRET_MAP_TYPE));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read local secret store " + path, exception);
        }
    }

    private void writeSecrets(Map<String, StoredSecret> secrets) {
        try {
            Files.createDirectories(path.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), secrets);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write local secret store " + path, exception);
        }
    }
}
