package ai.corporatedroneagent;

import java.util.Locale;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Provider clients are created lazily from local app settings in AiChatService.
@SpringBootApplication(excludeName = {
        "org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration",
        "org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAiAudioTranscriptionAutoConfiguration",
        "org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAiChatAutoConfiguration",
        "org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAiEmbeddingAutoConfiguration",
        "org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAiImageAutoConfiguration",
        "org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration",
        "org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionAutoConfiguration",
        "org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingAutoConfiguration",
        "org.springframework.ai.model.mistralai.autoconfigure.MistralAiChatAutoConfiguration",
        "org.springframework.ai.model.mistralai.autoconfigure.MistralAiEmbeddingAutoConfiguration",
        "org.springframework.ai.model.mistralai.autoconfigure.MistralAiModerationAutoConfiguration",
        "org.springframework.ai.model.mistralai.autoconfigure.MistralAiOcrAutoConfiguration",
        "org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration",
        "org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration",
        "org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration",
        "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration",
        "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration",
        "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration",
        "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration",
        "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration",
        "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration",
        "org.springframework.ai.model.openaisdk.autoconfigure.OpenAiSdkChatAutoConfiguration",
        "org.springframework.ai.model.openaisdk.autoconfigure.OpenAiSdkEmbeddingAutoConfiguration",
        "org.springframework.ai.model.openaisdk.autoconfigure.OpenAiSdkImageAutoConfiguration"
})
public class CorporateDroneAgentApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(CorporateDroneAgentApplication.class);
        application.setHeadless(shouldRunHeadless(args));
        application.run(args);
    }

    static boolean shouldRunHeadless(String[] args) {
        return shouldRunHeadless(
                args,
                System.getProperty("cda.browser.enabled"),
                System.getenv("CDA_BROWSER_ENABLED")
        );
    }

    static boolean shouldRunHeadless(String[] args, String browserEnabledProperty, String browserEnabledEnv) {
        return hasDisabledBrowserArgument(args)
                || isExplicitFalse(browserEnabledProperty)
                || isExplicitFalse(browserEnabledEnv);
    }

    private static boolean hasDisabledBrowserArgument(String[] args) {
        if (args == null) {
            return false;
        }

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg == null) {
                continue;
            }
            if (arg.startsWith("--cda.browser.enabled=")) {
                return isExplicitFalse(arg.substring("--cda.browser.enabled=".length()));
            }
            if ("--cda.browser.enabled".equals(arg) && i + 1 < args.length) {
                return isExplicitFalse(args[i + 1]);
            }
        }
        return false;
    }

    private static boolean isExplicitFalse(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "false".equals(normalized)
                || "0".equals(normalized)
                || "off".equals(normalized)
                || "no".equals(normalized);
    }
}
