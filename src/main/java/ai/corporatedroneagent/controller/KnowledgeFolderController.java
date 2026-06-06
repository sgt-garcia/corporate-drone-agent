package ai.corporatedroneagent.controller;

import ai.corporatedroneagent.dto.KnowledgeFolderRequest;
import ai.corporatedroneagent.model.KnowledgeFolder;
import ai.corporatedroneagent.service.KnowledgeFolderScanService;
import ai.corporatedroneagent.service.SettingsService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
    private final KnowledgeFolderScanService knowledgeFolderScanService;

    public KnowledgeFolderController(
            SettingsService settingsService,
            KnowledgeFolderScanService knowledgeFolderScanService
    ) {
        this.settingsService = settingsService;
        this.knowledgeFolderScanService = knowledgeFolderScanService;
    }

    @GetMapping
    public List<KnowledgeFolder> listKnowledgeFolders() {
        return settingsService.listKnowledgeFolders();
    }

    @PostMapping
    public KnowledgeFolder addKnowledgeFolder(@RequestBody KnowledgeFolderRequest request) {
        return settingsService.addKnowledgeFolder(request);
    }

    @DeleteMapping("/{folderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeKnowledgeFolder(@PathVariable UUID folderId) {
        settingsService.removeKnowledgeFolder(folderId);
    }

    @PostMapping("/{folderId}/scan")
    public KnowledgeFolder scanKnowledgeFolder(@PathVariable UUID folderId) {
        return knowledgeFolderScanService.scanFolder(folderId);
    }

    @PostMapping("/{folderId}/pause")
    public KnowledgeFolder pauseKnowledgeFolder(@PathVariable UUID folderId) {
        return settingsService.pauseKnowledgeFolder(folderId);
    }

    @PostMapping("/{folderId}/resume")
    public KnowledgeFolder resumeKnowledgeFolder(@PathVariable UUID folderId) {
        return settingsService.resumeKnowledgeFolder(folderId);
    }
}
