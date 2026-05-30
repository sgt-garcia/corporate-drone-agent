package ai.corporatedroneagent.dto;

public class GeminiModelsRequest {

    private String apiKey = "";
    private boolean useSavedKey = true;

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
