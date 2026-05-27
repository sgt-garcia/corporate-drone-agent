package ai.corporatedroneagent.model;

public class OpenAiOfficialSettings {

    private String apiKey = "";
    private String model = "gpt-5-mini";

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
