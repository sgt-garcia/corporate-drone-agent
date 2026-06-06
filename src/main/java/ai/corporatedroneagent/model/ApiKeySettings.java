package ai.corporatedroneagent.model;

public interface ApiKeySettings {

    String getApiKey();

    void setApiKey(String apiKey);

    boolean isClearApiKey();

    void setClearApiKey(boolean clearApiKey);

    boolean isApiKeyConfigured();

    void setApiKeyConfigured(boolean apiKeyConfigured);

    String getApiKeyLastFour();

    void setApiKeyLastFour(String apiKeyLastFour);
}
