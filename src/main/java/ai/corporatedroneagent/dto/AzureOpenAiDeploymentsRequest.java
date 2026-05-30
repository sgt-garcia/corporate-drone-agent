package ai.corporatedroneagent.dto;

public class AzureOpenAiDeploymentsRequest {

    private String endpoint = "";
    private String apiKey = "";
    private boolean useSavedKey = true;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isUseSavedKey() {
        return useSavedKey;
    }

    public void setUseSavedKey(boolean useSavedKey) {
        this.useSavedKey = useSavedKey;
    }
}
