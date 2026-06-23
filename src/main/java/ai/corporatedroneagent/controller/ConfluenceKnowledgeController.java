package ai.corporatedroneagent.controller;

import ai.corporatedroneagent.dto.ConfluenceConnectionRequest;
import ai.corporatedroneagent.dto.ConfluenceConnectionValidationDto;
import ai.corporatedroneagent.dto.ConfluenceSpaceDto;
import ai.corporatedroneagent.dto.ConfluenceSpaceRequest;
import ai.corporatedroneagent.model.ConfluenceSettings;
import ai.corporatedroneagent.service.ConfluenceSettingsService;
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

@RestController
@RequestMapping("/api/settings/knowledge/confluence")
public class ConfluenceKnowledgeController {

    private final ConfluenceSettingsService confluenceSettingsService;

    public ConfluenceKnowledgeController(ConfluenceSettingsService confluenceSettingsService) {
        this.confluenceSettingsService = confluenceSettingsService;
    }

    @GetMapping
    public ConfluenceSettings getConfluenceSettings() {
        return confluenceSettingsService.getConfluenceSettings();
    }

    @PostMapping("/connection/validate")
    public ConfluenceConnectionValidationDto validateConnection(@RequestBody ConfluenceConnectionRequest request) {
        return confluenceSettingsService.validateConfluenceConnection(request);
    }

    @PutMapping("/connection")
    public ConfluenceSettings saveConnection(@RequestBody ConfluenceConnectionRequest request) {
        return confluenceSettingsService.saveConfluenceConnection(request);
    }

    @DeleteMapping("/connection")
    public ConfluenceSettings clearConnection() {
        return confluenceSettingsService.clearConfluenceConnection();
    }

    @GetMapping("/spaces")
    public List<ConfluenceSpaceDto> listSpaces() {
        return confluenceSettingsService.listConfluenceSpaces();
    }

    @GetMapping("/spaces/search")
    public List<ConfluenceSpaceDto> searchSpaces(@RequestParam(defaultValue = "") String query) {
        return confluenceSettingsService.searchConfluenceSpaces(query);
    }

    @PostMapping("/spaces")
    public ConfluenceSpaceDto addSpace(@RequestBody ConfluenceSpaceRequest request) {
        return confluenceSettingsService.addConfluenceSpace(request);
    }

    @DeleteMapping("/spaces/{spaceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeSpace(@PathVariable String spaceId) {
        confluenceSettingsService.removeConfluenceSpace(spaceId);
    }

    @PostMapping("/spaces/{spaceId}/scan")
    public ConfluenceSpaceDto scanSpace(@PathVariable String spaceId) {
        return confluenceSettingsService.scanConfluenceSpace(spaceId);
    }

    @PostMapping("/spaces/{spaceId}/pause")
    public ConfluenceSpaceDto pauseSpace(@PathVariable String spaceId) {
        return confluenceSettingsService.pauseConfluenceSpace(spaceId);
    }

    @PostMapping("/spaces/{spaceId}/resume")
    public ConfluenceSpaceDto resumeSpace(@PathVariable String spaceId) {
        return confluenceSettingsService.resumeConfluenceSpace(spaceId);
    }
}
