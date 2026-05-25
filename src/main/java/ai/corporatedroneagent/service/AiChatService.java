package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.model.AzureOpenAiSettings;
import ai.corporatedroneagent.repository.SettingsRepository;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.stereotype.Service;

@Service
public class AiChatService {

    private final SettingsRepository settingsRepository;

    public AiChatService(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    public String reply(String userContent) {
        ApplicationSettings settings = settingsRepository.get();

        if (!"azure-openai".equals(settings.getAiModel())) {
            return echoReply(userContent);
        }

        AzureOpenAiSettings azureSettings = settings.getAzureOpenAi();
        if (isBlank(azureSettings.getEndpoint())
                || isBlank(azureSettings.getApiKey())
                || isBlank(azureSettings.getDeploymentName())) {
            return "Azure OpenAI is selected, but endpoint, API key, and deployment name are required before I can call it.";
        }

        try {
            AzureOpenAiChatModel chatModel = buildAzureChatModel(azureSettings);
            return chatModel.call(buildPrompt(settings, userContent));
        } catch (RuntimeException exception) {
            return "Azure OpenAI request failed: " + exception.getMessage();
        }
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

    private String buildPrompt(ApplicationSettings settings, String userContent) {
        if (isBlank(settings.getCustomInstructions())) {
            return userContent;
        }

        return settings.getCustomInstructions() + "\n\nUser message:\n" + userContent;
    }

    private String echoReply(String userContent) {
        return "You said:\n\n" + userContent;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
