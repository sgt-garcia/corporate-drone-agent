package ai.corporatedroneagent.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class ApiKeySettingsSupport implements ApiKeySettings {

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String apiKey = "";
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private boolean clearApiKey;
    private boolean apiKeyConfigured;
    private String apiKeyLastFour = "";

    @Override
    public String getApiKey() {
        return apiKey;
    }

    @Override
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public boolean isClearApiKey() {
        return clearApiKey;
    }

    @Override
    public void setClearApiKey(boolean clearApiKey) {
        this.clearApiKey = clearApiKey;
    }

    @Override
    public boolean isApiKeyConfigured() {
        return apiKeyConfigured;
    }

    @Override
    public void setApiKeyConfigured(boolean apiKeyConfigured) {
        this.apiKeyConfigured = apiKeyConfigured;
    }

    @Override
    public String getApiKeyLastFour() {
        return apiKeyLastFour;
    }

    @Override
    public void setApiKeyLastFour(String apiKeyLastFour) {
        this.apiKeyLastFour = apiKeyLastFour;
    }
}
