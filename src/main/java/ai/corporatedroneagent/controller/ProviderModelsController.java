package ai.corporatedroneagent.controller;

import ai.corporatedroneagent.dto.ApiKeyModelsRequest;
import ai.corporatedroneagent.dto.AzureOpenAiDeploymentsRequest;
import ai.corporatedroneagent.dto.BedrockModelsRequest;
import ai.corporatedroneagent.dto.OllamaModelsRequest;
import ai.corporatedroneagent.dto.OpenAiModelsRequest;
import ai.corporatedroneagent.service.AnthropicModelsService;
import ai.corporatedroneagent.service.AzureOpenAiDeploymentsService;
import ai.corporatedroneagent.service.BedrockModelsService;
import ai.corporatedroneagent.service.DeepSeekModelsService;
import ai.corporatedroneagent.service.GeminiModelsService;
import ai.corporatedroneagent.service.GroqModelsService;
import ai.corporatedroneagent.service.MistralModelsService;
import ai.corporatedroneagent.service.OllamaModelsService;
import ai.corporatedroneagent.service.OpenAiModelsService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class ProviderModelsController {

    private final OpenAiModelsService openAiModelsService;
    private final MistralModelsService mistralModelsService;
    private final AnthropicModelsService anthropicModelsService;
    private final GeminiModelsService geminiModelsService;
    private final OllamaModelsService ollamaModelsService;
    private final AzureOpenAiDeploymentsService azureOpenAiDeploymentsService;
    private final BedrockModelsService bedrockModelsService;
    private final GroqModelsService groqModelsService;
    private final DeepSeekModelsService deepSeekModelsService;

    public ProviderModelsController(
            OpenAiModelsService openAiModelsService,
            MistralModelsService mistralModelsService,
            AnthropicModelsService anthropicModelsService,
            GeminiModelsService geminiModelsService,
            OllamaModelsService ollamaModelsService,
            AzureOpenAiDeploymentsService azureOpenAiDeploymentsService,
            BedrockModelsService bedrockModelsService,
            GroqModelsService groqModelsService,
            DeepSeekModelsService deepSeekModelsService
    ) {
        this.openAiModelsService = openAiModelsService;
        this.mistralModelsService = mistralModelsService;
        this.anthropicModelsService = anthropicModelsService;
        this.geminiModelsService = geminiModelsService;
        this.ollamaModelsService = ollamaModelsService;
        this.azureOpenAiDeploymentsService = azureOpenAiDeploymentsService;
        this.bedrockModelsService = bedrockModelsService;
        this.groqModelsService = groqModelsService;
        this.deepSeekModelsService = deepSeekModelsService;
    }

    @PostMapping("/openai-models")
    public List<String> listOpenAiModels(@RequestBody OpenAiModelsRequest request) {
        return openAiModelsService.listModels(request);
    }

    @PostMapping("/mistral-models")
    public List<String> listMistralModels(@RequestBody ApiKeyModelsRequest request) {
        return mistralModelsService.listModels(request);
    }

    @PostMapping("/anthropic-models")
    public List<String> listAnthropicModels(@RequestBody ApiKeyModelsRequest request) {
        return anthropicModelsService.listModels(request);
    }

    @PostMapping("/gemini-models")
    public List<String> listGeminiModels(@RequestBody ApiKeyModelsRequest request) {
        return geminiModelsService.listModels(request);
    }

    @PostMapping("/ollama-models")
    public List<String> listOllamaModels(@RequestBody OllamaModelsRequest request) {
        return ollamaModelsService.listModels(request);
    }

    @PostMapping("/azure-openai-deployments")
    public List<String> listAzureOpenAiDeployments(@RequestBody AzureOpenAiDeploymentsRequest request) {
        return azureOpenAiDeploymentsService.listDeployments(request);
    }

    @GetMapping("/bedrock-regions")
    public List<String> listBedrockRegions() {
        return bedrockModelsService.listRegions();
    }

    @PostMapping("/bedrock-models")
    public List<String> listBedrockModels(@RequestBody BedrockModelsRequest request) {
        return bedrockModelsService.listModels(request);
    }

    @PostMapping("/groq-models")
    public List<String> listGroqModels(@RequestBody ApiKeyModelsRequest request) {
        return groqModelsService.listModels(request);
    }

    @PostMapping("/deepseek-models")
    public List<String> listDeepSeekModels(@RequestBody ApiKeyModelsRequest request) {
        return deepSeekModelsService.listModels(request);
    }
}
