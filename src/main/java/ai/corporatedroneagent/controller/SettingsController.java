package ai.corporatedroneagent.controller;

import ai.corporatedroneagent.dto.AnthropicModelsRequest;
import ai.corporatedroneagent.dto.AzureOpenAiDeploymentsRequest;
import ai.corporatedroneagent.dto.GoogleGeminiModelsRequest;
import ai.corporatedroneagent.dto.MistralModelsRequest;
import ai.corporatedroneagent.dto.OllamaModelsRequest;
import ai.corporatedroneagent.dto.OpenAiModelsRequest;
import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.service.AnthropicModelsService;
import ai.corporatedroneagent.service.AzureOpenAiDeploymentsService;
import ai.corporatedroneagent.service.GoogleGeminiModelsService;
import ai.corporatedroneagent.service.MistralModelsService;
import ai.corporatedroneagent.service.OllamaModelsService;
import ai.corporatedroneagent.service.OpenAiModelsService;
import ai.corporatedroneagent.service.SettingsService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;
    private final OpenAiModelsService openAiModelsService;
    private final MistralModelsService mistralModelsService;
    private final AnthropicModelsService anthropicModelsService;
    private final GoogleGeminiModelsService googleGeminiModelsService;
    private final OllamaModelsService ollamaModelsService;
    private final AzureOpenAiDeploymentsService azureOpenAiDeploymentsService;

    public SettingsController(
            SettingsService settingsService,
            OpenAiModelsService openAiModelsService,
            MistralModelsService mistralModelsService,
            AnthropicModelsService anthropicModelsService,
            GoogleGeminiModelsService googleGeminiModelsService,
            OllamaModelsService ollamaModelsService,
            AzureOpenAiDeploymentsService azureOpenAiDeploymentsService
    ) {
        this.settingsService = settingsService;
        this.openAiModelsService = openAiModelsService;
        this.mistralModelsService = mistralModelsService;
        this.anthropicModelsService = anthropicModelsService;
        this.googleGeminiModelsService = googleGeminiModelsService;
        this.ollamaModelsService = ollamaModelsService;
        this.azureOpenAiDeploymentsService = azureOpenAiDeploymentsService;
    }

    @GetMapping
    public ApplicationSettings getSettings() {
        return settingsService.get();
    }

    @PutMapping
    public ApplicationSettings saveSettings(@RequestBody ApplicationSettings settings) {
        return settingsService.save(settings);
    }

    @PostMapping("/openai-models")
    public List<String> listOpenAiModels(@RequestBody OpenAiModelsRequest request) {
        return openAiModelsService.listModels(request);
    }

    @PostMapping("/mistral-models")
    public List<String> listMistralModels(@RequestBody MistralModelsRequest request) {
        return mistralModelsService.listModels(request);
    }

    @PostMapping("/anthropic-models")
    public List<String> listAnthropicModels(@RequestBody AnthropicModelsRequest request) {
        return anthropicModelsService.listModels(request);
    }

    @PostMapping("/google-gemini-models")
    public List<String> listGoogleGeminiModels(@RequestBody GoogleGeminiModelsRequest request) {
        return googleGeminiModelsService.listModels(request);
    }

    @PostMapping("/ollama-models")
    public List<String> listOllamaModels(@RequestBody OllamaModelsRequest request) {
        return ollamaModelsService.listModels(request);
    }

    @PostMapping("/azure-openai-deployments")
    public List<String> listAzureOpenAiDeployments(@RequestBody AzureOpenAiDeploymentsRequest request) {
        return azureOpenAiDeploymentsService.listDeployments(request);
    }
}
