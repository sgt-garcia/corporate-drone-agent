package ai.corporatedroneagent.controller;

import ai.corporatedroneagent.dto.JiraConnectionRequest;
import ai.corporatedroneagent.dto.JiraConnectionValidationDto;
import ai.corporatedroneagent.dto.JiraProjectDto;
import ai.corporatedroneagent.dto.JiraProjectRequest;
import ai.corporatedroneagent.model.JiraSettings;
import ai.corporatedroneagent.model.knowledge.JiraKnowledgeRootConfig;
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
@RequestMapping("/api/settings/knowledge/jira")
public class JiraKnowledgeController {

    private final SettingsService settingsService;
    private final KnowledgeIngestionService ingestionService;
    private final KnowledgeRootRepository knowledgeRootRepository;

    public JiraKnowledgeController(
            SettingsService settingsService,
            KnowledgeIngestionService ingestionService,
            KnowledgeRootRepository knowledgeRootRepository
    ) {
        this.settingsService = settingsService;
        this.ingestionService = ingestionService;
        this.knowledgeRootRepository = knowledgeRootRepository;
    }

    @GetMapping
    public JiraSettings getJiraSettings() {
        return settingsService.getJiraSettings();
    }

    @PostMapping("/connection/validate")
    public JiraConnectionValidationDto validateConnection(@RequestBody JiraConnectionRequest request) {
        return settingsService.validateJiraConnection(request);
    }

    @PutMapping("/connection")
    public JiraSettings saveConnection(@RequestBody JiraConnectionRequest request) {
        return settingsService.saveJiraConnection(request);
    }

    @DeleteMapping("/connection")
    public JiraSettings clearConnection() {
        return settingsService.clearJiraConnection();
    }

    @GetMapping("/projects")
    public List<JiraProjectDto> listProjects() {
        return settingsService.listJiraProjects();
    }

    @GetMapping("/projects/search")
    public List<JiraProjectDto> searchProjects(@RequestParam(defaultValue = "") String query) {
        return settingsService.searchJiraProjects(query);
    }

    @PostMapping("/projects")
    public JiraProjectDto addProject(@RequestBody JiraProjectRequest request) {
        return settingsService.addJiraProject(request);
    }

    @DeleteMapping("/projects/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeProject(@PathVariable String projectId) {
        settingsService.removeJiraProject(projectId);
    }

    @PostMapping("/projects/{projectId}/scan")
    public JiraProjectDto scanProject(@PathVariable String projectId) {
        KnowledgeRoot root = knowledgeRootRepository.findBySource(KnowledgeSource.JIRA).stream()
                .filter(candidate -> projectId.equals(JiraKnowledgeRootConfig.readProjectId(candidate)))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jira project not found"));
        ingestionService.scanInBackground(root);
        return settingsService.getJiraSettings().getProjects().stream()
                .filter(project -> projectId.equals(project.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jira project not found"));
    }

    @PostMapping("/projects/{projectId}/pause")
    public JiraProjectDto pauseProject(@PathVariable String projectId) {
        return settingsService.pauseJiraProject(projectId);
    }

    @PostMapping("/projects/{projectId}/resume")
    public JiraProjectDto resumeProject(@PathVariable String projectId) {
        return settingsService.resumeJiraProject(projectId);
    }
}
