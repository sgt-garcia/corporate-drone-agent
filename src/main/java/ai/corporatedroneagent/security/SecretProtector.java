package ai.corporatedroneagent.security;

interface SecretProtector {

    StoredSecret protect(String secret);

    String unprotect(StoredSecret secret);

    static SecretProtector create() {
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return new WindowsDpapiSecretProtector();
        }

        String secretKey = System.getenv("CDA_SECRET_KEY");
        if (secretKey != null && !secretKey.isBlank()) {
            return new EnvironmentAesGcmSecretProtector(secretKey);
        }

        return new UnavailableSecretProtector();
    }
}
