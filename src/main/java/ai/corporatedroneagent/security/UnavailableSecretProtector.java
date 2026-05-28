package ai.corporatedroneagent.security;

class UnavailableSecretProtector implements SecretProtector {

    @Override
    public StoredSecret protect(String secret) {
        throw new IllegalStateException(
                "Secret storage is unavailable. On Windows this app uses DPAPI. "
                        + "On other platforms set CDA_SECRET_KEY to enable AES-GCM local secret storage."
        );
    }

    @Override
    public String unprotect(StoredSecret secret) {
        throw new IllegalStateException(
                "Secret storage is unavailable. Set CDA_SECRET_KEY to read AES-GCM local secrets."
        );
    }
}
