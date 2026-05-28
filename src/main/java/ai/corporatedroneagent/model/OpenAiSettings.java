package ai.corporatedroneagent.model;

public class OpenAiSettings {

    private String apiKey = "";
    private String model = "gpt-5.5";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
