package ai.corporatedroneagent.model;

public class ApplicationSettings {

    private String agentName = "Corporate Drone Agent";
    private String aiModel = "none";
    private String customInstructions = "Answer with concise, practical guidance using available local project context first.";
    private OpenAiSettings openAi = new OpenAiSettings();
    private OpenAiOfficialSettings openAiOfficial = new OpenAiOfficialSettings();
    private AzureOpenAiSettings azureOpenAi = new AzureOpenAiSettings();
    private OllamaSettings ollama = new OllamaSettings();
    private MistralAiSettings mistralAi = new MistralAiSettings();
    private GoogleGenAiSettings googleGenAi = new GoogleGenAiSettings();
    private AnthropicSettings anthropic = new AnthropicSettings();

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getAiModel() {
        return aiModel;
    }

    public void setAiModel(String aiModel) {
        this.aiModel = aiModel;
    }

    public String getCustomInstructions() {
        return customInstructions;
    }

    public void setCustomInstructions(String customInstructions) {
        this.customInstructions = customInstructions;
    }

    public OpenAiSettings getOpenAi() {
        return openAi;
    }

    public void setOpenAi(OpenAiSettings openAi) {
        this.openAi = openAi == null ? new OpenAiSettings() : openAi;
    }

    public OpenAiOfficialSettings getOpenAiOfficial() {
        return openAiOfficial;
    }

    public void setOpenAiOfficial(OpenAiOfficialSettings openAiOfficial) {
        this.openAiOfficial = openAiOfficial == null ? new OpenAiOfficialSettings() : openAiOfficial;
    }

    public AzureOpenAiSettings getAzureOpenAi() {
        return azureOpenAi;
    }

    public void setAzureOpenAi(AzureOpenAiSettings azureOpenAi) {
        this.azureOpenAi = azureOpenAi == null ? new AzureOpenAiSettings() : azureOpenAi;
    }

    public OllamaSettings getOllama() {
        return ollama;
    }

    public void setOllama(OllamaSettings ollama) {
        this.ollama = ollama == null ? new OllamaSettings() : ollama;
    }

    public MistralAiSettings getMistralAi() {
        return mistralAi;
    }

    public void setMistralAi(MistralAiSettings mistralAi) {
        this.mistralAi = mistralAi == null ? new MistralAiSettings() : mistralAi;
    }

    public GoogleGenAiSettings getGoogleGenAi() {
        return googleGenAi;
    }

    public void setGoogleGenAi(GoogleGenAiSettings googleGenAi) {
        this.googleGenAi = googleGenAi == null ? new GoogleGenAiSettings() : googleGenAi;
    }

    public AnthropicSettings getAnthropic() {
        return anthropic;
    }

    public void setAnthropic(AnthropicSettings anthropic) {
        this.anthropic = anthropic == null ? new AnthropicSettings() : anthropic;
    }
}
