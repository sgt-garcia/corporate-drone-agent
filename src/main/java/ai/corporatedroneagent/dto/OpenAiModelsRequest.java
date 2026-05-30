package ai.corporatedroneagent.dto;

public class OpenAiModelsRequest {

    private String provider = "openai";
    private String apiKey = "";
    private boolean useSavedKey = true;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
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
