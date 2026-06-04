package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.KnowledgeFolderRequest;
import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.model.KnowledgeFolder;
import ai.corporatedroneagent.repository.SettingsRepository;
import ai.corporatedroneagent.util.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SettingsService {

    private static final int MAX_KNOWLEDGE_FOLDERS = 10;

    private final SettingsRepository settingsRepository;
    private final SettingsSecretsService settingsSecretsService;
    private final EventService eventService;

    public SettingsService(
            SettingsRepository settingsRepository,
            SettingsSecretsService settingsSecretsService,
            EventService eventService
    ) {
        this.settingsRepository = settingsRepository;
        this.settingsSecretsService = settingsSecretsService;
        this.eventService = eventService;
    }

    public ApplicationSettings get() {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        normalizeKnowledgeFolders(settings);
        settingsSecretsService.clearSecretValues(settings);
        settingsSecretsService.applySecretStatus(settings);
        return settings;
    }

    public ApplicationSettings getWithSecrets() {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        normalizeKnowledgeFolders(settings);
        settingsSecretsService.applySecretValues(settings);
        return settings;
    }

    public ApplicationSettings save(ApplicationSettings settings) {
        ApplicationSettings current = settingsRepository.get();
        migratePlaintextSecrets(current);
        settingsSecretsService.saveSubmittedSecrets(settings);

        current.setAgentName(Strings.defaultIfBlank(settings.getAgentName(), "Corporate Drone's Agent"));
        current.setAiModel(Strings.defaultIfBlank(settings.getAiModel(), "none"));
        current.setCustomInstructions(Strings.emptyIfNull(settings.getCustomInstructions()));
        current.setOpenAi(settings.getOpenAi());
        current.setOpenAiSdk(settings.getOpenAiSdk());
        current.setAzureOpenAi(settings.getAzureOpenAi());
        current.setOllama(settings.getOllama());
        current.setMistral(settings.getMistral());
        current.setGemini(settings.getGemini());
        current.setAnthropic(settings.getAnthropic());
        current.setGroq(settings.getGroq());
        current.setDeepSeek(settings.getDeepSeek());
        current.setKnowledgeFolders(sanitizeKnowledgeFolders(settings.getKnowledgeFolders()));
        settingsSecretsService.clearSecretValues(current);
        settingsSecretsService.applySecretStatus(current);
        settingsRepository.save(current);
        eventService.publish("settings-updated", current);
        return current;
    }

    public synchronized List<KnowledgeFolder> listKnowledgeFolders() {
        return get().getKnowledgeFolders();
    }

    public synchronized KnowledgeFolder addKnowledgeFolder(KnowledgeFolderRequest request) {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        normalizeKnowledgeFolders(settings);

        String path = Strings.defaultIfBlank(request == null ? "" : request.path(), "");
        if (path.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder path is required");
        }
        if (settings.getKnowledgeFolders().size() >= MAX_KNOWLEDGE_FOLDERS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Local folder limit reached");
        }
        if (settings.getKnowledgeFolders().stream().anyMatch(folder -> samePath(folder.getPath(), path))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Folder is already configured");
        }

        KnowledgeFolder folder = new KnowledgeFolder();
        folder.setId(UUID.randomUUID());
        folder.setPath(path);
        folder.setStatus("scanned");
        settings.getKnowledgeFolders().add(folder);
        saveAndPublish(settings);
        return folder;
    }

    public synchronized void removeKnowledgeFolder(UUID folderId) {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        normalizeKnowledgeFolders(settings);

        boolean removed = settings.getKnowledgeFolders().removeIf(folder -> folderId.equals(folder.getId()));
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge folder not found");
        }

        saveAndPublish(settings);
    }

    public synchronized KnowledgeFolder scanKnowledgeFolder(UUID folderId) {
        ApplicationSettings settings = get();
        return findKnowledgeFolder(settings, folderId);
    }

    public synchronized KnowledgeFolder pauseKnowledgeFolder(UUID folderId) {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        normalizeKnowledgeFolders(settings);
        KnowledgeFolder folder = findKnowledgeFolder(settings, folderId);
        folder.setStatus("paused");
        folder.setNextScan("");
        saveAndPublish(settings);
        return folder;
    }

    public synchronized KnowledgeFolder resumeKnowledgeFolder(UUID folderId) {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        normalizeKnowledgeFolders(settings);
        KnowledgeFolder folder = findKnowledgeFolder(settings, folderId);
        folder.setStatus("scanned");
        saveAndPublish(settings);
        return folder;
    }

    private void migratePlaintextSecrets(ApplicationSettings settings) {
        boolean migrated = settingsSecretsService.migratePlaintextSecrets(settings);
        if (migrated) {
            settingsSecretsService.applySecretStatus(settings);
            settingsRepository.save(settings);
        }
    }

    private void saveAndPublish(ApplicationSettings settings) {
        settingsSecretsService.clearSecretValues(settings);
        settingsSecretsService.applySecretStatus(settings);
        settingsRepository.save(settings);
        eventService.publish("settings-updated", settings);
    }

    private KnowledgeFolder findKnowledgeFolder(ApplicationSettings settings, UUID folderId) {
        return settings.getKnowledgeFolders().stream()
                .filter(folder -> folderId.equals(folder.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge folder not found"));
    }

    private void normalizeKnowledgeFolders(ApplicationSettings settings) {
        settings.setKnowledgeFolders(sanitizeKnowledgeFolders(settings.getKnowledgeFolders()));
    }

    private List<KnowledgeFolder> sanitizeKnowledgeFolders(List<KnowledgeFolder> folders) {
        List<KnowledgeFolder> sanitized = new ArrayList<>();
        if (folders == null) {
            return sanitized;
        }

        for (KnowledgeFolder folder : folders) {
            if (folder == null || folder.getPath() == null || folder.getPath().isBlank()) {
                continue;
            }
            KnowledgeFolder sanitizedFolder = new KnowledgeFolder();
            sanitizedFolder.setId(folder.getId() == null ? UUID.randomUUID() : folder.getId());
            sanitizedFolder.setPath(folder.getPath().trim());
            sanitizedFolder.setStatus("paused".equals(folder.getStatus()) ? "paused" : "scanned");
            sanitizedFolder.setFiles(folder.getFiles());
            sanitizedFolder.setSize(folder.getSize());
            sanitizedFolder.setNextScan(folder.getNextScan());
            if (sanitized.stream().noneMatch(existing -> samePath(existing.getPath(), sanitizedFolder.getPath()))) {
                sanitized.add(sanitizedFolder);
            }
            if (sanitized.size() == MAX_KNOWLEDGE_FOLDERS) {
                break;
            }
        }
        return sanitized;
    }

    private boolean samePath(String first, String second) {
        return Strings.defaultIfBlank(first, "").equalsIgnoreCase(Strings.defaultIfBlank(second, ""));
    }
}
