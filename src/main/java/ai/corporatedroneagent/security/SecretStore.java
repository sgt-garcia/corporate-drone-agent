package ai.corporatedroneagent.security;

import java.util.Optional;

public interface SecretStore {

    Optional<String> get(String key);

    void put(String key, String secret);

    void delete(String key);
}
