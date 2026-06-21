package ai.corporatedroneagent.dto;

public class OpenAiModelsRequest extends ApiKeyModelsRequest {

    private String provider = "openai";

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}
