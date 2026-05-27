package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.model.AzureOpenAiSettings;
import ai.corporatedroneagent.model.OpenAiOfficialSettings;
import ai.corporatedroneagent.model.OpenAiSettings;
import ai.corporatedroneagent.repository.SettingsRepository;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import java.util.UUID;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openaisdk.OpenAiSdkChatModel;
import org.springframework.ai.openaisdk.OpenAiSdkChatOptions;
import org.springframework.stereotype.Service;

@Service
public class AiChatService {

    private static final int MAX_MEMORY_MESSAGES = 20;

    private final SettingsRepository settingsRepository;
    private final ChatMemory chatMemory;

    public AiChatService(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
        this.chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(MAX_MEMORY_MESSAGES)
                .build();
    }

    public String reply(UUID conversationId, String userContent) {
        ApplicationSettings settings = settingsRepository.get();

        return switch (settings.getAiModel()) {
            case "azure-openai" -> azureOpenAiReply(conversationId, settings, userContent);
            case "openai" -> openAiReply(conversationId, settings, userContent);
            case "openai-official" -> openAiOfficialReply(conversationId, settings, userContent);
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

    private String openAiOfficialReply(UUID conversationId, ApplicationSettings settings, String userContent) {
        OpenAiOfficialSettings openAiSettings = settings.getOpenAiOfficial();
        if (isBlank(openAiSettings.getApiKey()) || isBlank(openAiSettings.getModel())) {
            return "OpenAI (Official) is selected, but API key and model are required before I can call it.";
        }

        try {
            return chatModelReply(
                    conversationId,
                    settings,
                    buildOpenAiOfficialChatModel(openAiSettings),
                    userContent
            );
        } catch (RuntimeException exception) {
            return "OpenAI (Official) request failed: " + exception.getMessage();
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

        OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                .model(settings.getModel())
                .temperature(0.2)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(chatOptions)
                .build();
    }

    private OpenAiSdkChatModel buildOpenAiOfficialChatModel(OpenAiOfficialSettings settings) {
        OpenAiSdkChatOptions chatOptions = OpenAiSdkChatOptions.builder()
                .apiKey(settings.getApiKey())
                .model(settings.getModel())
                .temperature(0.2)
                .build();

        return new OpenAiSdkChatModel(chatOptions);
    }

    private AzureOpenAiChatModel buildAzureChatModel(AzureOpenAiSettings settings) {
        OpenAIClientBuilder clientBuilder = new OpenAIClientBuilder()
                .credential(new AzureKeyCredential(settings.getApiKey()))
                .endpoint(settings.getEndpoint());

        AzureOpenAiChatOptions chatOptions = AzureOpenAiChatOptions.builder()
                .deploymentName(settings.getDeploymentName())
                .temperature(0.2)
                .build();

        return AzureOpenAiChatModel.builder()
                .openAIClientBuilder(clientBuilder)
                .defaultOptions(chatOptions)
                .build();
    }

    private String echoReply(String userContent) {
        return "You said:\n\n" + userContent;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
