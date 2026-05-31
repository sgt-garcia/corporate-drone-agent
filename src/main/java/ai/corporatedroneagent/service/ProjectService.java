package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.ConversationSummaryDto;
import ai.corporatedroneagent.dto.ProjectDto;
import ai.corporatedroneagent.dto.ProjectRequest;
import ai.corporatedroneagent.model.Conversation;
import ai.corporatedroneagent.model.Project;
import ai.corporatedroneagent.repository.ConversationRepository;
import ai.corporatedroneagent.repository.ProjectRepository;
import ai.corporatedroneagent.util.Strings;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectService {

    private static final String DEFAULT_PROJECT_INSTRUCTIONS =
            "Use this project context when answering questions about planning, decisions, and follow-up work.";

    private final ProjectRepository projectRepository;
    private final ConversationRepository conversationRepository;
    private final EventService eventService;

    public ProjectService(
            ProjectRepository projectRepository,
            ConversationRepository conversationRepository,
            EventService eventService
    ) {
        this.projectRepository = projectRepository;
        this.conversationRepository = conversationRepository;
        this.eventService = eventService;
    }

    public synchronized List<ProjectDto> listProjects() {
        ensureDefaultData();
        return projectRepository.findAll().stream()
                .sorted(Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toDto)
                .toList();
    }

    public synchronized ProjectDto create(ProjectRequest request) {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setName(Strings.defaultIfBlank(request.name(), "New Project"));
        project.setWorkingFolder(Strings.emptyIfNull(request.workingFolder()));
        project.setCustomInstructions(Strings.defaultIfBlank(request.customInstructions(), DEFAULT_PROJECT_INSTRUCTIONS));
        projectRepository.save(project);
        ProjectDto dto = toDto(project);
        eventService.publish("projects-updated", listProjects());
        return dto;
    }

    public synchronized ProjectDto update(UUID projectId, ProjectRequest request) {
        Project project = getProject(projectId);
        project.setName(Strings.defaultIfBlank(request.name(), project.getName()));
        project.setWorkingFolder(Strings.emptyIfNull(request.workingFolder()));
        project.setCustomInstructions(Strings.emptyIfNull(request.customInstructions()));
        projectRepository.save(project);
        ProjectDto dto = toDto(project);
        eventService.publish("project-updated", dto);
        eventService.publish("projects-updated", listProjects());
        return dto;
    }

    public synchronized List<ConversationSummaryDto> listConversations(UUID projectId) {
        Project project = getProject(projectId);
        return project.getConversationIds().stream()
                .map(conversationRepository::findById)
                .flatMap(optional -> optional.stream())
                .map(this::toSummary)
                .toList();
    }

    public synchronized Project getProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private ProjectDto toDto(Project project) {
        List<ConversationSummaryDto> conversations = project.getConversationIds().stream()
                .map(conversationRepository::findById)
                .flatMap(optional -> optional.stream())
                .map(this::toSummary)
                .toList();
        return new ProjectDto(
                project.getId(),
                project.getName(),
                project.getWorkingFolder(),
                project.getCustomInstructions(),
                conversations
        );
    }

    private ConversationSummaryDto toSummary(Conversation conversation) {
        return new ConversationSummaryDto(conversation.getId(), conversation.getProjectId(), conversation.getName());
    }

    private void ensureDefaultData() {
        if (!projectRepository.findAll().isEmpty()) {
            return;
        }

        createSeedProject("New Project", List.of("New Conversation"));
    }

    private void createSeedProject(String name, List<String> conversationNames) {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setName(name);
        project.setWorkingFolder("");
        project.setCustomInstructions(DEFAULT_PROJECT_INSTRUCTIONS);

        for (String conversationName : conversationNames) {
            Conversation conversation = new Conversation();
            conversation.setId(UUID.randomUUID());
            conversation.setProjectId(project.getId());
            conversation.setName(conversationName);
            conversationRepository.save(conversation);
            project.getConversationIds().add(conversation.getId());
        }

        projectRepository.save(project);
    }
}
