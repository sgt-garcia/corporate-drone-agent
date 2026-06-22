package ai.corporatedroneagent.controller;

import ai.corporatedroneagent.dto.KnowledgeFolderDto;
import ai.corporatedroneagent.dto.KnowledgeFolderRequest;
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

    public KnowledgeFolderController(SettingsService settingsService) {
        this.settingsService = settingsService;
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
        return settingsService.scanKnowledgeFolder(folderId);
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
