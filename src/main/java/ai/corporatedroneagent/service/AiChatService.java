package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.model.AnthropicSettings;
import ai.corporatedroneagent.model.AzureOpenAiSettings;
import ai.corporatedroneagent.model.Conversation;
import ai.corporatedroneagent.model.DeepSeekSettings;
import ai.corporatedroneagent.model.GoogleGeminiSettings;
import ai.corporatedroneagent.model.GroqSettings;
import ai.corporatedroneagent.model.Message;
import ai.corporatedroneagent.model.MistralAiSettings;
import ai.corporatedroneagent.model.OllamaSettings;
import ai.corporatedroneagent.model.OpenAiOfficialSdkSettings;
import ai.corporatedroneagent.model.OpenAiSettings;
import ai.corporatedroneagent.model.Project;
import ai.corporatedroneagent.repository.ConversationRepository;
import ai.corporatedroneagent.repository.ProjectRepository;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.google.genai.Client;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
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

    private final SettingsService settingsService;
    private final ConversationRepository conversationRepository;
    private final ProjectRepository projectRepository;

    public AiChatService(
            SettingsService settingsService,
            ConversationRepository conversationRepository,
            ProjectRepository projectRepository
    ) {
        this.settingsService = settingsService;
        this.conversationRepository = conversationRepository;
        this.projectRepository = projectRepository;
    }

    public String reply(UUID conversationId, String userContent) {
        ApplicationSettings settings = settingsService.getWithSecrets();
        Conversation conversation = getConversation(conversationId);
        Project project = getProject(conversation.getProjectId());

        return switch (settings.getAiModel()) {
            case "azure-openai" -> azureOpenAiReply(settings, conversation, project);
            case "openai" -> openAiReply(settings, conversation, project);
            case "openai-official", "openai-official-sdk" -> openAiOfficialSdkReply(settings, conversation, project);
            case "ollama" -> ollamaReply(settings, conversation, project);
            case "mistral-ai" -> mistralAiReply(settings, conversation, project);
            case "google-genai", "google-gemini" -> googleGeminiReply(settings, conversation, project);
            case "anthropic" -> anthropicReply(settings, conversation, project);
            case "groq" -> groqReply(settings, conversation, project);
            case "deepseek" -> deepSeekReply(settings, conversation, project);
            default -> echoReply(userContent);
        };
    }

    private String openAiReply(ApplicationSettings settings, Conversation conversation, Project project) {
        OpenAiSettings openAiSettings = settings.getOpenAi();
        if (isBlank(openAiSettings.getApiKey()) || isBlank(openAiSettings.getModel())) {
            return "OpenAI is selected, but API key and model are required before I can call it.";
        }

        try {
            return chatModelReply(
                    settings,
                    buildOpenAiChatModel(openAiSettings),
                    conversation,
                    project
            );
        } catch (RuntimeException exception) {
            return "OpenAI request failed: " + exception.getMessage();
        }
    }

    private String openAiOfficialSdkReply(ApplicationSettings settings, Conversation conversation, Project project) {
        OpenAiOfficialSdkSettings openAiSettings = settings.getOpenAiOfficialSdk();
        if (isBlank(openAiSettings.getApiKey()) || isBlank(openAiSettings.getModel())) {
            return "OpenAI (SDK) is selected, but API key and model are required before I can call it.";
        }

        try {
            return chatModelReply(
                    settings,
                    buildOpenAiOfficialSdkChatModel(openAiSettings),
                    conversation,
                    project
            );
        } catch (RuntimeException exception) {
            return "OpenAI (SDK) request failed: " + exception.getMessage();
        }
    }

    private String azureOpenAiReply(ApplicationSettings settings, Conversation conversation, Project project) {
        AzureOpenAiSettings azureSettings = settings.getAzureOpenAi();
        if (isBlank(azureSettings.getEndpoint())
                || isBlank(azureSettings.getApiKey())
                || isBlank(azureSettings.getDeploymentName())) {
            return "Azure OpenAI is selected, but endpoint, API key, and deployment name are required before I can call it.";
        }

        try {
            return chatModelReply(
                    settings,
                    buildAzureChatModel(azureSettings),
                    conversation,
                    project
            );
        } catch (RuntimeException exception) {
            return "Azure OpenAI request failed: " + exception.getMessage();
        }
    }

    private String ollamaReply(ApplicationSettings settings, Conversation conversation, Project project) {
        OllamaSettings ollamaSettings = settings.getOllama();
        if (isBlank(ollamaSettings.getBaseUrl()) || isBlank(ollamaSettings.getModel())) {
            return "Ollama is selected, but base URL and model are required before I can call it.";
        }

        try {
            return chatModelReply(
                    settings,
                    buildOllamaChatModel(ollamaSettings),
                    conversation,
                    project
            );
        } catch (RuntimeException exception) {
            return "Ollama request failed: " + exception.getMessage();
        }
    }

    private String mistralAiReply(ApplicationSettings settings, Conversation conversation, Project project) {
        MistralAiSettings mistralAiSettings = settings.getMistralAi();
        if (isBlank(mistralAiSettings.getApiKey()) || isBlank(mistralAiSettings.getModel())) {
            return "Mistral is selected, but API key and model are required before I can call it.";
        }

        try {
            return chatModelReply(
                    settings,
                    buildMistralAiChatModel(mistralAiSettings),
                    conversation,
                    project
            );
        } catch (RuntimeException exception) {
            return "Mistral request failed: " + exception.getMessage();
        }
    }

    private String googleGeminiReply(ApplicationSettings settings, Conversation conversation, Project project) {
        GoogleGeminiSettings googleGeminiSettings = settings.getGoogleGemini();
        if (isBlank(googleGeminiSettings.getApiKey()) || isBlank(googleGeminiSettings.getModel())) {
            return "Gemini is selected, but API key and model are required before I can call it.";
        }

        GoogleGenAiChatModel chatModel = buildGoogleGeminiChatModel(googleGeminiSettings);
        try {
            return chatModelReply(
                    settings,
                    chatModel,
                    conversation,
                    project
            );
        } catch (RuntimeException exception) {
            return "Gemini request failed: " + exception.getMessage();
        } finally {
            destroyGoogleGeminiChatModel(chatModel);
        }
    }

    private String anthropicReply(ApplicationSettings settings, Conversation conversation, Project project) {
        AnthropicSettings anthropicSettings = settings.getAnthropic();
        if (isBlank(anthropicSettings.getApiKey()) || isBlank(anthropicSettings.getModel())) {
            return "Anthropic is selected, but API key and model are required before I can call it.";
        }

        try {
            return chatModelReply(
                    settings,
                    buildAnthropicChatModel(anthropicSettings),
                    conversation,
                    project
            );
        } catch (RuntimeException exception) {
            return "Anthropic request failed: " + exception.getMessage();
        }
    }

    private String groqReply(ApplicationSettings settings, Conversation conversation, Project project) {
        GroqSettings groqSettings = settings.getGroq();
        if (isBlank(groqSettings.getApiKey()) || isBlank(groqSettings.getModel())) {
            return "Groq is selected, but API key and model are required before I can call it.";
        }

        try {
            return chatModelReply(
                    settings,
                    buildGroqChatModel(groqSettings),
                    conversation,
                    project
            );
        } catch (RuntimeException exception) {
            return "Groq request failed: " + exception.getMessage();
        }
    }

    private String deepSeekReply(ApplicationSettings settings, Conversation conversation, Project project) {
        DeepSeekSettings deepSeekSettings = settings.getDeepSeek();
        if (isBlank(deepSeekSettings.getApiKey()) || isBlank(deepSeekSettings.getModel())) {
            return "DeepSeek is selected, but API key and model are required before I can call it.";
        }

        try {
            return chatModelReply(
                    settings,
                    buildDeepSeekChatModel(deepSeekSettings),
                    conversation,
                    project
            );
        } catch (RuntimeException exception) {
            return "DeepSeek request failed: " + exception.getMessage();
        }
    }

    private String chatModelReply(
            ApplicationSettings settings,
            ChatModel chatModel,
            Conversation conversation,
            Project project
    ) {
        ChatClient chatClient = ChatClient.builder(chatModel).build();

        return chatClient.prompt()
                .messages(buildPromptMessages(settings, project, conversation))
                .call()
                .content();
    }

    static List<org.springframework.ai.chat.messages.Message> buildPromptMessages(
            ApplicationSettings settings,
            Project project,
            Conversation conversation
    ) {
        List<org.springframework.ai.chat.messages.Message> promptMessages = new ArrayList<>();
        String systemInstructions = composeSystemInstructions(settings, project);
        if (!isBlank(systemInstructions)) {
            promptMessages.add(new SystemMessage(systemInstructions));
        }

        for (Message message : conversation.getMessages()) {
            if (isBlank(message.getContent())) {
                continue;
            }

            String role = message.getRole() == null ? "" : message.getRole().toLowerCase(Locale.ROOT);
            switch (role) {
                case "user" -> promptMessages.add(new UserMessage(message.getContent()));
                case "assistant" -> promptMessages.add(new AssistantMessage(message.getContent()));
                default -> {
                }
            }
        }

        return promptMessages;
    }

    static String composeSystemInstructions(ApplicationSettings settings, Project project) {
        List<String> sections = new ArrayList<>();
        if (!isBlank(settings.getCustomInstructions())) {
            sections.add("Global instructions:\n" + settings.getCustomInstructions().trim());
        }
        if (!isBlank(project.getCustomInstructions())) {
            sections.add("Project instructions:\n" + project.getCustomInstructions().trim());
        }
        return String.join("\n\n", sections);
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

    private OpenAiChatModel buildGroqChatModel(GroqSettings settings) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl("https://api.groq.com/openai")
                .apiKey(settings.getApiKey())
                .build();

        OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                .model(settings.getModel())
                .temperature(0.2)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(chatOptions)
                .build();
    }

    private OpenAiChatModel buildDeepSeekChatModel(DeepSeekSettings settings) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl("https://api.deepseek.com")
                .completionsPath("/chat/completions")
                .apiKey(settings.getApiKey())
                .build();

        OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                .model(settings.getModel())
                .temperature(0.2)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(chatOptions)
                .build();
    }

    private String echoReply(String userContent) {
        return "You said:\n\n" + userContent;
    }

    private Conversation getConversation(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalStateException("Conversation not found: " + conversationId));
    }

    private Project getProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalStateException("Project not found: " + projectId));
    }

    private static boolean isBlank(String value) {
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
