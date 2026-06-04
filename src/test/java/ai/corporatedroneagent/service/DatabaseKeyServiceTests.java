package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import ai.corporatedroneagent.security.SecretStore;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DatabaseKeyServiceTests {

    @Test
    void createsAndReusesDatabaseEncryptionKey() {
        InMemorySecretStore secretStore = new InMemorySecretStore();
        DatabaseKeyService service = new DatabaseKeyService(secretStore, new SecureRandom(new byte[]{1, 2, 3, 4}));

        String key = service.encryptionKey();
        String reused = service.encryptionKey();

        assertThat(key).isNotBlank();
        assertThat(reused).isEqualTo(key);
    }

    private static class InMemorySecretStore implements SecretStore {

        private final Map<String, String> secrets = new HashMap<>();

        @Override
        public Optional<String> get(String key) {
            return Optional.ofNullable(secrets.get(key));
        }

        @Override
        public void put(String key, String secret) {
            secrets.put(key, secret);
        }

        @Override
        public void delete(String key) {
            secrets.remove(key);
        }
    }
}
