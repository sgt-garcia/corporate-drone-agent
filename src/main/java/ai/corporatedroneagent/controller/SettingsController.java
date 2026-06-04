package ai.corporatedroneagent.controller;

import ai.corporatedroneagent.dto.AnthropicModelsRequest;
import ai.corporatedroneagent.dto.AzureOpenAiDeploymentsRequest;
import ai.corporatedroneagent.dto.DeepSeekModelsRequest;
import ai.corporatedroneagent.dto.GeminiModelsRequest;
import ai.corporatedroneagent.dto.GroqModelsRequest;
import ai.corporatedroneagent.dto.KnowledgeFolderRequest;
import ai.corporatedroneagent.dto.MistralModelsRequest;
import ai.corporatedroneagent.dto.OllamaModelsRequest;
import ai.corporatedroneagent.dto.OpenAiModelsRequest;
import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.model.KnowledgeFolder;
import ai.corporatedroneagent.service.AnthropicModelsService;
import ai.corporatedroneagent.service.AzureOpenAiDeploymentsService;
import ai.corporatedroneagent.service.DeepSeekModelsService;
import ai.corporatedroneagent.service.GeminiModelsService;
import ai.corporatedroneagent.service.GroqModelsService;
import ai.corporatedroneagent.service.KnowledgeFolderScanService;
import ai.corporatedroneagent.service.MistralModelsService;
import ai.corporatedroneagent.service.OllamaModelsService;
import ai.corporatedroneagent.service.OpenAiModelsService;
import ai.corporatedroneagent.service.SettingsService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;
    private final OpenAiModelsService openAiModelsService;
    private final MistralModelsService mistralModelsService;
    private final AnthropicModelsService anthropicModelsService;
    private final GeminiModelsService geminiModelsService;
    private final OllamaModelsService ollamaModelsService;
    private final AzureOpenAiDeploymentsService azureOpenAiDeploymentsService;
    private final GroqModelsService groqModelsService;
    private final DeepSeekModelsService deepSeekModelsService;
    private final KnowledgeFolderScanService knowledgeFolderScanService;

    public SettingsController(
            SettingsService settingsService,
            OpenAiModelsService openAiModelsService,
            MistralModelsService mistralModelsService,
            AnthropicModelsService anthropicModelsService,
            GeminiModelsService geminiModelsService,
            OllamaModelsService ollamaModelsService,
            AzureOpenAiDeploymentsService azureOpenAiDeploymentsService,
            GroqModelsService groqModelsService,
            DeepSeekModelsService deepSeekModelsService,
            KnowledgeFolderScanService knowledgeFolderScanService
    ) {
        this.settingsService = settingsService;
        this.openAiModelsService = openAiModelsService;
        this.mistralModelsService = mistralModelsService;
        this.anthropicModelsService = anthropicModelsService;
        this.geminiModelsService = geminiModelsService;
        this.ollamaModelsService = ollamaModelsService;
        this.azureOpenAiDeploymentsService = azureOpenAiDeploymentsService;
        this.groqModelsService = groqModelsService;
        this.deepSeekModelsService = deepSeekModelsService;
        this.knowledgeFolderScanService = knowledgeFolderScanService;
    }

    @GetMapping
    public ApplicationSettings getSettings() {
        return settingsService.get();
    }

    @PutMapping
    public ApplicationSettings saveSettings(@RequestBody ApplicationSettings settings) {
        return settingsService.save(settings);
    }

    @GetMapping("/knowledge/local-folders")
    public List<KnowledgeFolder> listKnowledgeFolders() {
        return settingsService.listKnowledgeFolders();
    }

    @PostMapping("/knowledge/local-folders")
    public KnowledgeFolder addKnowledgeFolder(@RequestBody KnowledgeFolderRequest request) {
        return settingsService.addKnowledgeFolder(request);
    }

    @DeleteMapping("/knowledge/local-folders/{folderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeKnowledgeFolder(@PathVariable UUID folderId) {
        settingsService.removeKnowledgeFolder(folderId);
    }

    @PostMapping("/knowledge/local-folders/{folderId}/scan")
    public KnowledgeFolder scanKnowledgeFolder(@PathVariable UUID folderId) {
        return knowledgeFolderScanService.scanFolder(folderId);
    }

    @PostMapping("/knowledge/local-folders/{folderId}/pause")
    public KnowledgeFolder pauseKnowledgeFolder(@PathVariable UUID folderId) {
        return settingsService.pauseKnowledgeFolder(folderId);
    }

    @PostMapping("/knowledge/local-folders/{folderId}/resume")
    public KnowledgeFolder resumeKnowledgeFolder(@PathVariable UUID folderId) {
        return settingsService.resumeKnowledgeFolder(folderId);
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

    @PostMapping("/gemini-models")
    public List<String> listGeminiModels(@RequestBody GeminiModelsRequest request) {
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

    @PostMapping("/groq-models")
    public List<String> listGroqModels(@RequestBody GroqModelsRequest request) {
        return groqModelsService.listModels(request);
    }

    @PostMapping("/deepseek-models")
    public List<String> listDeepSeekModels(@RequestBody DeepSeekModelsRequest request) {
        return deepSeekModelsService.listModels(request);
    }
}
