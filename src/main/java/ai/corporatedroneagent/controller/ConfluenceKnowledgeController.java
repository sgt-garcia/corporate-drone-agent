package ai.corporatedroneagent.controller;

import ai.corporatedroneagent.dto.ConfluenceConnectionRequest;
import ai.corporatedroneagent.dto.ConfluenceConnectionValidationDto;
import ai.corporatedroneagent.dto.ConfluenceSpaceDto;
import ai.corporatedroneagent.dto.ConfluenceSpaceRequest;
import ai.corporatedroneagent.model.ConfluenceSettings;
import ai.corporatedroneagent.model.knowledge.ConfluenceKnowledgeRootConfig;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
import ai.corporatedroneagent.service.KnowledgeIngestionService;
import ai.corporatedroneagent.service.SettingsService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/settings/knowledge/confluence")
public class ConfluenceKnowledgeController {

    private final SettingsService settingsService;
    private final KnowledgeIngestionService ingestionService;
    private final KnowledgeRootRepository knowledgeRootRepository;

    public ConfluenceKnowledgeController(
            SettingsService settingsService,
            KnowledgeIngestionService ingestionService,
            KnowledgeRootRepository knowledgeRootRepository
    ) {
        this.settingsService = settingsService;
        this.ingestionService = ingestionService;
        this.knowledgeRootRepository = knowledgeRootRepository;
    }

    @GetMapping
    public ConfluenceSettings getConfluenceSettings() {
        return settingsService.getConfluenceSettings();
    }

    @PostMapping("/connection/validate")
    public ConfluenceConnectionValidationDto validateConnection(@RequestBody ConfluenceConnectionRequest request) {
        return settingsService.validateConfluenceConnection(request);
    }

    @PutMapping("/connection")
    public ConfluenceSettings saveConnection(@RequestBody ConfluenceConnectionRequest request) {
        return settingsService.saveConfluenceConnection(request);
    }

    @DeleteMapping("/connection")
    public ConfluenceSettings clearConnection() {
        return settingsService.clearConfluenceConnection();
    }

    @GetMapping("/spaces")
    public List<ConfluenceSpaceDto> listSpaces() {
        return settingsService.listConfluenceSpaces();
    }

    @GetMapping("/spaces/search")
    public List<ConfluenceSpaceDto> searchSpaces(@RequestParam(defaultValue = "") String query) {
        return settingsService.searchConfluenceSpaces(query);
    }

    @PostMapping("/spaces")
    public ConfluenceSpaceDto addSpace(@RequestBody ConfluenceSpaceRequest request) {
        return settingsService.addConfluenceSpace(request);
    }

    @DeleteMapping("/spaces/{spaceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeSpace(@PathVariable String spaceId) {
        settingsService.removeConfluenceSpace(spaceId);
    }

    @PostMapping("/spaces/{spaceId}/scan")
    public ConfluenceSpaceDto scanSpace(@PathVariable String spaceId) {
        KnowledgeRoot root = knowledgeRootRepository.findBySource(KnowledgeSource.CONFLUENCE).stream()
                .filter(candidate -> spaceId.equals(ConfluenceKnowledgeRootConfig.readSpaceId(candidate)))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Confluence space not found"));
        ingestionService.scan(root);
        return settingsService.getConfluenceSettings().getSpaces().stream()
                .filter(space -> spaceId.equals(space.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Confluence space not found"));
    }

    @PostMapping("/spaces/{spaceId}/pause")
    public ConfluenceSpaceDto pauseSpace(@PathVariable String spaceId) {
        return settingsService.pauseConfluenceSpace(spaceId);
    }

    @PostMapping("/spaces/{spaceId}/resume")
    public ConfluenceSpaceDto resumeSpace(@PathVariable String spaceId) {
        return settingsService.resumeConfluenceSpace(spaceId);
    }
}
