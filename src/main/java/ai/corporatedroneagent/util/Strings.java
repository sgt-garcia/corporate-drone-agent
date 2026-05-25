package ai.corporatedroneagent.util;

public final class Strings {

    private Strings() {
    }

    public static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}
