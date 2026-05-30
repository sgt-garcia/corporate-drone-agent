package ai.corporatedroneagent.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public class ApplicationSettings {

    private String agentName = "Corporate Drone Agent";
    private String aiModel = "none";
    private String customInstructions = "Answer with concise, practical guidance using available local project context first.";
    private OpenAiSettings openAi = new OpenAiSettings();
    @JsonAlias("openAiOfficial")
    private OpenAiOfficialSdkSettings openAiOfficialSdk = new OpenAiOfficialSdkSettings();
    private AzureOpenAiSettings azureOpenAi = new AzureOpenAiSettings();
    private OllamaSettings ollama = new OllamaSettings();
    private MistralAiSettings mistralAi = new MistralAiSettings();
    @JsonAlias("googleGenAi")
    private GoogleGeminiSettings googleGemini = new GoogleGeminiSettings();
    private AnthropicSettings anthropic = new AnthropicSettings();
    private GroqSettings groq = new GroqSettings();
    private DeepSeekSettings deepSeek = new DeepSeekSettings();

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

    public OpenAiOfficialSdkSettings getOpenAiOfficialSdk() {
        return openAiOfficialSdk;
    }

    public void setOpenAiOfficialSdk(OpenAiOfficialSdkSettings openAiOfficialSdk) {
        this.openAiOfficialSdk = openAiOfficialSdk == null ? new OpenAiOfficialSdkSettings() : openAiOfficialSdk;
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

    public GoogleGeminiSettings getGoogleGemini() {
        return googleGemini;
    }

    public void setGoogleGemini(GoogleGeminiSettings googleGemini) {
        this.googleGemini = googleGemini == null ? new GoogleGeminiSettings() : googleGemini;
    }

    public AnthropicSettings getAnthropic() {
        return anthropic;
    }

    public void setAnthropic(AnthropicSettings anthropic) {
        this.anthropic = anthropic == null ? new AnthropicSettings() : anthropic;
    }

    public GroqSettings getGroq() {
        return groq;
    }

    public void setGroq(GroqSettings groq) {
        this.groq = groq == null ? new GroqSettings() : groq;
    }

    public DeepSeekSettings getDeepSeek() {
        return deepSeek;
    }

    public void setDeepSeek(DeepSeekSettings deepSeek) {
        this.deepSeek = deepSeek == null ? new DeepSeekSettings() : deepSeek;
    }
}
