package ai.corporatedroneagent.controller;

import ai.corporatedroneagent.dto.KnowledgeFolderDto;
import ai.corporatedroneagent.dto.KnowledgeFolderRequest;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
import ai.corporatedroneagent.service.KnowledgeIngestionService;
import ai.corporatedroneagent.service.SettingsService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/knowledge/local-folders")
public class KnowledgeFolderController {

    private final SettingsService settingsService;
    private final KnowledgeIngestionService ingestionService;
    private final KnowledgeRootRepository knowledgeRootRepository;

    public KnowledgeFolderController(
            SettingsService settingsService,
            KnowledgeIngestionService ingestionService,
            KnowledgeRootRepository knowledgeRootRepository
    ) {
        this.settingsService = settingsService;
        this.ingestionService = ingestionService;
        this.knowledgeRootRepository = knowledgeRootRepository;
    }

    @GetMapping
    public List<KnowledgeFolderDto> listKnowledgeFolders() {
        return settingsService.listKnowledgeFolders();
    }

    @PostMapping
    public KnowledgeFolderDto addKnowledgeFolder(@RequestBody KnowledgeFolderRequest request) {
        return settingsService.addKnowledgeFolder(request);
    }

    @DeleteMapping("/{folderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeKnowledgeFolder(@PathVariable UUID folderId) {
        settingsService.removeKnowledgeFolder(folderId);
    }

    @PostMapping("/{folderId}/scan")
    public KnowledgeFolderDto scanKnowledgeFolder(@PathVariable UUID folderId) {
        KnowledgeRoot root = knowledgeRootRepository.findByIdAndSource(folderId, KnowledgeSource.LOCAL_FOLDER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge folder not found"));
        ingestionService.scan(root);
        return settingsService.listKnowledgeFolders().stream()
                .filter(folder -> folderId.equals(folder.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge folder not found"));
    }

    @PostMapping("/{folderId}/pause")
    public KnowledgeFolderDto pauseKnowledgeFolder(@PathVariable UUID folderId) {
        return settingsService.pauseKnowledgeFolder(folderId);
    }

    @PostMapping("/{folderId}/resume")
    public KnowledgeFolderDto resumeKnowledgeFolder(@PathVariable UUID folderId) {
        return settingsService.resumeKnowledgeFolder(folderId);
    }
}
