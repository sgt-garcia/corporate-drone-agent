package ai.corporatedroneagent.model;

public class OllamaSettings {

    private String baseUrl = "http://localhost:11434";
    private String model = "gemma4";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
