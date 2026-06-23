package ai.corporatedroneagent.controller;

import ai.corporatedroneagent.dto.JiraConnectionRequest;
import ai.corporatedroneagent.dto.JiraConnectionValidationDto;
import ai.corporatedroneagent.dto.JiraProjectDto;
import ai.corporatedroneagent.dto.JiraProjectRequest;
import ai.corporatedroneagent.model.JiraSettings;
import ai.corporatedroneagent.service.JiraSettingsService;
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
@RequestMapping("/api/settings/knowledge/jira")
public class JiraKnowledgeController {

    private final JiraSettingsService jiraSettingsService;

    public JiraKnowledgeController(JiraSettingsService jiraSettingsService) {
        this.jiraSettingsService = jiraSettingsService;
    }

    @GetMapping
    public JiraSettings getJiraSettings() {
        return jiraSettingsService.getJiraSettings();
    }

    @PostMapping("/connection/validate")
    public JiraConnectionValidationDto validateConnection(@RequestBody JiraConnectionRequest request) {
        return jiraSettingsService.validateJiraConnection(request);
    }

    @PutMapping("/connection")
    public JiraSettings saveConnection(@RequestBody JiraConnectionRequest request) {
        return jiraSettingsService.saveJiraConnection(request);
    }

    @DeleteMapping("/connection")
    public JiraSettings clearConnection() {
        return jiraSettingsService.clearJiraConnection();
    }

    @GetMapping("/projects")
    public List<JiraProjectDto> listProjects() {
        return jiraSettingsService.listJiraProjects();
    }

    @GetMapping("/projects/search")
    public List<JiraProjectDto> searchProjects(@RequestParam(defaultValue = "") String query) {
        return jiraSettingsService.searchJiraProjects(query);
    }

    @PostMapping("/projects")
    public JiraProjectDto addProject(@RequestBody JiraProjectRequest request) {
        return jiraSettingsService.addJiraProject(request);
    }

    @DeleteMapping("/projects/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeProject(@PathVariable String projectId) {
        jiraSettingsService.removeJiraProject(projectId);
    }

    @PostMapping("/projects/{projectId}/scan")
    public JiraProjectDto scanProject(@PathVariable String projectId) {
        return jiraSettingsService.scanJiraProject(projectId);
    }

    @PostMapping("/projects/{projectId}/pause")
    public JiraProjectDto pauseProject(@PathVariable String projectId) {
        return jiraSettingsService.pauseJiraProject(projectId);
    }

    @PostMapping("/projects/{projectId}/resume")
    public JiraProjectDto resumeProject(@PathVariable String projectId) {
        return jiraSettingsService.resumeJiraProject(projectId);
    }
}
