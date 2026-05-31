package ai.corporatedroneagent.controller;

import ai.corporatedroneagent.dto.ConversationDto;
import ai.corporatedroneagent.dto.ConversationRequest;
import ai.corporatedroneagent.dto.ConversationSummaryDto;
import ai.corporatedroneagent.dto.ProjectDto;
import ai.corporatedroneagent.dto.ProjectRequest;
import ai.corporatedroneagent.service.ConversationService;
import ai.corporatedroneagent.service.ProjectService;
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
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ConversationService conversationService;

    public ProjectController(ProjectService projectService, ConversationService conversationService) {
        this.projectService = projectService;
        this.conversationService = conversationService;
    }

    @GetMapping
    public List<ProjectDto> listProjects() {
        return projectService.listProjects();
    }

    @PostMapping
    public ProjectDto createProject(@RequestBody ProjectRequest request) {
        return projectService.create(request);
    }

    @PutMapping("/{projectId}")
    public ProjectDto updateProject(@PathVariable UUID projectId, @RequestBody ProjectRequest request) {
        return projectService.update(projectId, request);
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProject(@PathVariable UUID projectId) {
        projectService.delete(projectId);
    }

    @GetMapping("/{projectId}/conversations")
    public List<ConversationSummaryDto> listConversations(@PathVariable UUID projectId) {
        return projectService.listConversations(projectId);
    }

    @PostMapping("/{projectId}/conversations")
    public ConversationDto createConversation(
            @PathVariable UUID projectId,
            @RequestBody ConversationRequest request
    ) {
        return conversationService.create(projectId, request);
    }
}
