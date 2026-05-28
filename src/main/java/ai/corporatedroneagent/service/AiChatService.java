package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.model.AnthropicSettings;
import ai.corporatedroneagent.model.AzureOpenAiSettings;
import ai.corporatedroneagent.model.GoogleGeminiSettings;
import ai.corporatedroneagent.model.MistralAiSettings;
import ai.corporatedroneagent.model.OllamaSettings;
import ai.corporatedroneagent.model.OpenAiOfficialSdkSettings;
import ai.corporatedroneagent.model.OpenAiSettings;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.google.genai.Client;
import java.util.UUID;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.ai.mistralai.MistralAiChatOptions;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openaisdk.OpenAiSdkChatModel;
import org.springframework.ai.openaisdk.OpenAiSdkChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Service;

@Service
public class AiChatService {

    private static final int MAX_MEMORY_MESSAGES = 20;

    private final SettingsService settingsService;
    private final ChatMemory chatMemory;

    public AiChatService(SettingsService settingsService) {
        this.settingsService = settingsService;
        this.chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(MAX_MEMORY_MESSAGES)
                .build();
    }

    public String reply(UUID conversationId, String userContent) {
        ApplicationSettings settings = settingsService.getWithSecrets();

        return switch (settings.getAiModel()) {
            case "azure-openai" -> azureOpenAiReply(conversationId, settings, userContent);
            case "openai" -> openAiReply(conversationId, settings, userContent);
            case "openai-official", "openai-official-sdk" -> openAiOfficialSdkReply(conversationId, settings, userContent);
            case "ollama" -> ollamaReply(conversationId, settings, userContent);
            case "mistral-ai" -> mistralAiReply(conversationId, settings, userContent);
            case "google-genai", "google-gemini" -> googleGeminiReply(conversationId, settings, userContent);
            case "anthropic" -> anthropicReply(conversationId, settings, userContent);
            default -> echoReply(userContent);
        };
    }

    private String openAiReply(UUID conversationId, ApplicationSettings settings, String userContent) {
        OpenAiSettings openAiSettings = settings.getOpenAi();
        if (isBlank(openAiSettings.getApiKey()) || isBlank(openAiSettings.getModel())) {
            return "OpenAI is selected, but API key and model are required before I can call it.";
        }

        try {
            return chatModelReply(
                    conversationId,
                    settings,
                    buildOpenAiChatModel(openAiSettings),
                    userContent
            );
        } catch (RuntimeException exception) {
            return "OpenAI request failed: " + exception.getMessage();
        }
    }

    private String openAiOfficialSdkReply(UUID conversationId, ApplicationSettings settings, String userContent) {
        OpenAiOfficialSdkSettings openAiSettings = settings.getOpenAiOfficialSdk();
        if (isBlank(openAiSettings.getApiKey()) || isBlank(openAiSettings.getModel())) {
            return "OpenAI (Official SDK) is selected, but API key and model are required before I can call it.";
        }

        try {
            return chatModelReply(
                    conversationId,
                    settings,
                    buildOpenAiOfficialSdkChatModel(openAiSettings),
                    userContent
            );
        } catch (RuntimeException exception) {
            return "OpenAI (Official SDK) request failed: " + exception.getMessage();
        }
    }

    private String azureOpenAiReply(UUID conversationId, ApplicationSettings settings, String userContent) {
        AzureOpenAiSettings azureSettings = settings.getAzureOpenAi();
        if (isBlank(azureSettings.getEndpoint())
                || isBlank(azureSettings.getApiKey())
                || isBlank(azureSettings.getDeploymentName())) {
            return "Azure OpenAI is selected, but endpoint, API key, and deployment name are required before I can call it.";
        }

        try {
            return chatModelReply(
                    conversationId,
                    settings,
                    buildAzureChatModel(azureSettings),
                    userContent
            );
        } catch (RuntimeException exception) {
            return "Azure OpenAI request failed: " + exception.getMessage();
        }
    }

    private String ollamaReply(UUID conversationId, ApplicationSettings settings, String userContent) {
        OllamaSettings ollamaSettings = settings.getOllama();
        if (isBlank(ollamaSettings.getBaseUrl()) || isBlank(ollamaSettings.getModel())) {
            return "Ollama is selected, but base URL and model are required before I can call it.";
        }

        try {
            return chatModelReply(
                    conversationId,
                    settings,
                    buildOllamaChatModel(ollamaSettings),
                    userContent
            );
        } catch (RuntimeException exception) {
            return "Ollama request failed: " + exception.getMessage();
        }
    }

    private String mistralAiReply(UUID conversationId, ApplicationSettings settings, String userContent) {
        MistralAiSettings mistralAiSettings = settings.getMistralAi();
        if (isBlank(mistralAiSettings.getApiKey()) || isBlank(mistralAiSettings.getModel())) {
            return "Mistral AI is selected, but API key and model are required before I can call it.";
        }

        try {
            return chatModelReply(
                    conversationId,
                    settings,
                    buildMistralAiChatModel(mistralAiSettings),
                    userContent
            );
        } catch (RuntimeException exception) {
            return "Mistral AI request failed: " + exception.getMessage();
        }
    }

    private String googleGeminiReply(UUID conversationId, ApplicationSettings settings, String userContent) {
        GoogleGeminiSettings googleGeminiSettings = settings.getGoogleGemini();
        if (isBlank(googleGeminiSettings.getApiKey()) || isBlank(googleGeminiSettings.getModel())) {
            return "Google Gemini is selected, but API key and model are required before I can call it.";
        }

        GoogleGenAiChatModel chatModel = buildGoogleGeminiChatModel(googleGeminiSettings);
        try {
            return chatModelReply(
                    conversationId,
                    settings,
                    chatModel,
                    userContent
            );
        } catch (RuntimeException exception) {
            return "Google Gemini request failed: " + exception.getMessage();
        } finally {
            destroyGoogleGeminiChatModel(chatModel);
        }
    }

    private String anthropicReply(UUID conversationId, ApplicationSettings settings, String userContent) {
        AnthropicSettings anthropicSettings = settings.getAnthropic();
        if (isBlank(anthropicSettings.getApiKey()) || isBlank(anthropicSettings.getModel())) {
            return "Anthropic is selected, but API key and model are required before I can call it.";
        }

        try {
            return chatModelReply(
                    conversationId,
                    settings,
                    buildAnthropicChatModel(anthropicSettings),
                    userContent
            );
        } catch (RuntimeException exception) {
            return "Anthropic request failed: " + exception.getMessage();
        }
    }

    private String chatModelReply(
            UUID conversationId,
            ApplicationSettings settings,
            ChatModel chatModel,
            String userContent
    ) {
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        ChatClient.ChatClientRequestSpec request = chatClient.prompt()
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId.toString()));

        if (!isBlank(settings.getCustomInstructions())) {
            request = request.system(settings.getCustomInstructions());
        }

        return request.user(userContent).call().content();
    }

    private OpenAiChatModel buildOpenAiChatModel(OpenAiSettings settings) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(settings.getApiKey())
                .build();

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(settings.getModel());

        if (supportsCustomTemperature(settings.getModel())) {
            optionsBuilder.temperature(0.2);
        }

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(optionsBuilder.build())
                .build();
    }

    private OpenAiSdkChatModel buildOpenAiOfficialSdkChatModel(OpenAiOfficialSdkSettings settings) {
        OpenAiSdkChatOptions.Builder optionsBuilder = OpenAiSdkChatOptions.builder()
                .apiKey(settings.getApiKey())
                .model(settings.getModel());

        if (supportsCustomTemperature(settings.getModel())) {
            optionsBuilder.temperature(0.2);
        }

        return new OpenAiSdkChatModel(optionsBuilder.build());
    }

    private AzureOpenAiChatModel buildAzureChatModel(AzureOpenAiSettings settings) {
        OpenAIClientBuilder clientBuilder = new OpenAIClientBuilder()
                .credential(new AzureKeyCredential(settings.getApiKey()))
                .endpoint(settings.getEndpoint());

        AzureOpenAiChatOptions.Builder optionsBuilder = AzureOpenAiChatOptions.builder()
                .deploymentName(settings.getDeploymentName());

        if (supportsCustomTemperature(settings.getDeploymentName())) {
            optionsBuilder.temperature(0.2);
        }

        return AzureOpenAiChatModel.builder()
                .openAIClientBuilder(clientBuilder)
                .defaultOptions(optionsBuilder.build())
                .build();
    }

    private OllamaChatModel buildOllamaChatModel(OllamaSettings settings) {
        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(settings.getBaseUrl())
                .build();

        OllamaChatOptions chatOptions = OllamaChatOptions.builder()
                .model(settings.getModel())
                .temperature(0.2)
                .build();

        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(chatOptions)
                .build();
    }

    private MistralAiChatModel buildMistralAiChatModel(MistralAiSettings settings) {
        MistralAiApi mistralAiApi = MistralAiApi.builder()
                .apiKey(settings.getApiKey())
                .build();

        MistralAiChatOptions chatOptions = MistralAiChatOptions.builder()
                .model(settings.getModel())
                .temperature(0.2)
                .build();

        return MistralAiChatModel.builder()
                .mistralAiApi(mistralAiApi)
                .defaultOptions(chatOptions)
                .build();
    }

    private GoogleGenAiChatModel buildGoogleGeminiChatModel(GoogleGeminiSettings settings) {
        Client genAiClient = Client.builder()
                .apiKey(settings.getApiKey())
                .build();

        GoogleGenAiChatOptions chatOptions = GoogleGenAiChatOptions.builder()
                .model(settings.getModel())
                .temperature(0.2)
                .build();

        return GoogleGenAiChatModel.builder()
                .genAiClient(genAiClient)
                .defaultOptions(chatOptions)
                .build();
    }

    private void destroyGoogleGeminiChatModel(GoogleGenAiChatModel chatModel) {
        try {
            chatModel.destroy();
        } catch (Exception exception) {
            // Keep provider cleanup failures from replacing the chat response or original request error.
        }
    }

    private AnthropicChatModel buildAnthropicChatModel(AnthropicSettings settings) {
        AnthropicApi anthropicApi = AnthropicApi.builder()
                .apiKey(settings.getApiKey())
                .build();

        AnthropicChatOptions chatOptions = AnthropicChatOptions.builder()
                .model(settings.getModel())
                .temperature(0.2)
                .maxTokens(4096)
                .build();

        return AnthropicChatModel.builder()
                .anthropicApi(anthropicApi)
                .defaultOptions(chatOptions)
                .build();
    }

    private String echoReply(String userContent) {
        return "You said:\n\n" + userContent;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean supportsCustomTemperature(String model) {
        if (model == null) {
            return true;
        }

        String normalizedModel = model.toLowerCase();
        return !normalizedModel.startsWith("gpt-5")
                && !normalizedModel.startsWith("o1")
                && !normalizedModel.startsWith("o3")
                && !normalizedModel.startsWith("o4")
                && !normalizedModel.contains("codex");
    }
}
