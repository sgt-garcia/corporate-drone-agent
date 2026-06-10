package ai.corporatedroneagent.model;

import ai.corporatedroneagent.dto.KnowledgeFolderDto;
import java.util.ArrayList;
import java.util.List;

public class ApplicationSettings {

    private String agentName = "Corporate Drone's Agent";
    private String aiModel = "none";
    private String customInstructions = "Answer with concise, practical guidance using available local project context first.";
    private boolean filesystemToolEnabled = true;
    private List<KnowledgeFolderDto> knowledgeFolders = new ArrayList<>();
    private OpenAiSettings openAi = new OpenAiSettings();
    private OpenAiSdkSettings openAiSdk = new OpenAiSdkSettings();
    private AzureOpenAiSettings azureOpenAi = new AzureOpenAiSettings();
    private OllamaSettings ollama = new OllamaSettings();
    private MistralSettings mistral = new MistralSettings();
    private GeminiSettings gemini = new GeminiSettings();
    private AnthropicSettings anthropic = new AnthropicSettings();
    private BedrockSettings bedrock = new BedrockSettings();
    private GroqSettings groq = new GroqSettings();
    private DeepSeekSettings deepSeek = new DeepSeekSettings();
    private JiraSettings jira = new JiraSettings();

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

    public boolean isFilesystemToolEnabled() {
        return filesystemToolEnabled;
    }

    public void setFilesystemToolEnabled(boolean filesystemToolEnabled) {
        this.filesystemToolEnabled = filesystemToolEnabled;
    }

    public List<KnowledgeFolderDto> getKnowledgeFolders() {
        return knowledgeFolders;
    }

    public void setKnowledgeFolders(List<KnowledgeFolderDto> knowledgeFolders) {
        this.knowledgeFolders = knowledgeFolders == null ? new ArrayList<>() : knowledgeFolders;
    }

    public OpenAiSettings getOpenAi() {
        return openAi;
    }

    public void setOpenAi(OpenAiSettings openAi) {
        this.openAi = openAi == null ? new OpenAiSettings() : openAi;
    }

    public OpenAiSdkSettings getOpenAiSdk() {
        return openAiSdk;
    }

    public void setOpenAiSdk(OpenAiSdkSettings openAiSdk) {
        this.openAiSdk = openAiSdk == null ? new OpenAiSdkSettings() : openAiSdk;
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

    public MistralSettings getMistral() {
        return mistral;
    }

    public void setMistral(MistralSettings mistral) {
        this.mistral = mistral == null ? new MistralSettings() : mistral;
    }

    public GeminiSettings getGemini() {
        return gemini;
    }

    public void setGemini(GeminiSettings gemini) {
        this.gemini = gemini == null ? new GeminiSettings() : gemini;
    }

    public AnthropicSettings getAnthropic() {
        return anthropic;
    }

    public void setAnthropic(AnthropicSettings anthropic) {
        this.anthropic = anthropic == null ? new AnthropicSettings() : anthropic;
    }

    public BedrockSettings getBedrock() {
        return bedrock;
    }

    public void setBedrock(BedrockSettings bedrock) {
        this.bedrock = bedrock == null ? new BedrockSettings() : bedrock;
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

    public JiraSettings getJira() {
        return jira;
    }

    public void setJira(JiraSettings jira) {
        this.jira = jira == null ? new JiraSettings() : jira;
    }
}
