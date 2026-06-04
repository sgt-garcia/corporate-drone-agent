package ai.corporatedroneagent.service;

import ai.corporatedroneagent.security.SecretStore;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DatabaseKeyService {

    private static final String SECRET_KEY = "knowledge.database.encryptionKey";
    private static final int KEY_BYTES = 32;

    private final SecretStore secretStore;
    private final SecureRandom secureRandom;

    @Autowired
    public DatabaseKeyService(SecretStore secretStore) {
        this(secretStore, new SecureRandom());
    }

    DatabaseKeyService(SecretStore secretStore, SecureRandom secureRandom) {
        this.secretStore = secretStore;
        this.secureRandom = secureRandom;
    }

    public synchronized String encryptionKey() {
        return secretStore.get(SECRET_KEY).orElseGet(this::createAndStoreKey);
    }

    private String createAndStoreKey() {
        byte[] key = new byte[KEY_BYTES];
        secureRandom.nextBytes(key);
        String encodedKey = Base64.getUrlEncoder().withoutPadding().encodeToString(key);
        secretStore.put(SECRET_KEY, encodedKey);
        return encodedKey;
    }
}
