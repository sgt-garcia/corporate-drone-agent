package ai.corporatedroneagent.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MistralSettings {

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String apiKey = "";
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private boolean clearApiKey;
    private boolean apiKeyConfigured;
    private String apiKeyLastFour = "";
    private String model = "";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isClearApiKey() {
        return clearApiKey;
    }

    public void setClearApiKey(boolean clearApiKey) {
        this.clearApiKey = clearApiKey;
    }

    public boolean isApiKeyConfigured() {
        return apiKeyConfigured;
    }

    public void setApiKeyConfigured(boolean apiKeyConfigured) {
        this.apiKeyConfigured = apiKeyConfigured;
    }

    public String getApiKeyLastFour() {
        return apiKeyLastFour;
    }

    public void setApiKeyLastFour(String apiKeyLastFour) {
        this.apiKeyLastFour = apiKeyLastFour;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
