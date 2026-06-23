package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.knowledge.KnowledgeResource;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceConversion;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import ai.corporatedroneagent.repository.KnowledgeResourcePipelineRepository;
import ai.corporatedroneagent.repository.KnowledgeResourceRepository;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeSearchService {

    private static final Pattern JIRA_ISSUE_KEY = Pattern.compile("\\b[A-Z][A-Z0-9_]+-\\d+\\b", Pattern.CASE_INSENSITIVE);
    private static final int DEFAULT_RESULT_LENGTH = 3000;
    private static final int DEFAULT_DOCUMENT_LENGTH = 50_000;
    // How many candidates a reference/title can resolve to before we just take the best one; small
    // because references are nearly unique and we only return the first that has converted text.
    private static final int FETCH_CANDIDATES = 5;

    private final KnowledgeIndexingService indexingService;
    private final KnowledgeResourcePipelineRepository pipelineRepository;
    private final KnowledgeResourceRepository resourceRepository;
    private final KnowledgeRootRepository rootRepository;

    public KnowledgeSearchService(
            KnowledgeIndexingService indexingService,
            KnowledgeResourcePipelineRepository pipelineRepository,
            KnowledgeResourceRepository resourceRepository,
            KnowledgeRootRepository rootRepository
    ) {
        this.indexingService = indexingService;
        this.pipelineRepository = pipelineRepository;
        this.resourceRepository = resourceRepository;
        this.rootRepository = rootRepository;
    }

    public List<KnowledgeContextSnippet> search(String query, int limit) {
        return search(query, limit, DEFAULT_RESULT_LENGTH);
    }

    public List<KnowledgeContextSnippet> search(String query, int limit, int maxResultLength) {
        if (limit <= 0) {
            return List.of();
        }
        int passageLength = maxResultLength <= 0 ? DEFAULT_RESULT_LENGTH : maxResultLength;

        List<KnowledgeContextSnippet> snippets = new ArrayList<>();
        Set<UUID> seenResources = new LinkedHashSet<>();
        for (String issueKey : issueKeys(query)) {
            if (snippets.size() >= limit) {
                break;
            }
            addJiraIssueSnippets(issueKey, limit, passageLength, seenResources, snippets);
        }

        if (snippets.size() >= limit) {
            return snippets;
        }

        for (KnowledgeIndexingService.KnowledgeIndexHit hit : indexingService.search(query, limit)) {
            if (seenResources.contains(hit.resourceId())) {
                continue;
            }
            Optional<KnowledgeContextSnippet> snippet = toSnippet(hit, query, passageLength);
            if (snippet.isPresent()) {
                snippets.add(snippet.get());
                seenResources.add(hit.resourceId());
            }
            if (snippets.size() >= limit) {
                break;
            }
        }
        return snippets;
    }

    /**
     * Returns the full extracted text of a single document matched by its reference or title (the
     * identifier a search result showed), capped at {@code maxLength}. Unlike {@link #search} this
     * does not centre on the query — the model already located the document and wants the whole of
     * it. The first match that has converted text wins; references are effectively unique.
     */
    public Optional<KnowledgeDocument> fetchDocument(String identifier, int maxLength) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        int cap = maxLength <= 0 ? DEFAULT_DOCUMENT_LENGTH : maxLength;
        for (KnowledgeResource resource : resourceRepository.findActiveByReferenceOrName(identifier.trim(), FETCH_CANDIDATES)) {
            Optional<String> text = conversionText(resource.getId());
            if (text.isEmpty()) {
                continue;
            }
            String full = text.get().strip();
            boolean truncated = full.length() > cap;
            String content = truncated ? full.substring(0, cap).strip() + " …" : full;
            KnowledgeRoot root = rootRepository.findById(resource.getRootId()).orElseGet(KnowledgeRoot::new);
            return Optional.of(new KnowledgeDocument(
                    root.getSource() == null ? "" : root.getSource().name(),
                    root.getDisplayName(),
                    resource.getReference(),
                    resource.getDisplayName(),
                    content,
                    truncated,
                    full.length()
            ));
        }
        return Optional.empty();
    }

    /**
     * Lists the connected knowledge roots so a caller can discover what is searchable before
     * querying — source type, display name, document count, scan status, and whether it is paused.
     */
    public List<KnowledgeSourceSummary> listSources() {
        List<KnowledgeSourceSummary> summaries = new ArrayList<>();
        for (KnowledgeRoot root : rootRepository.findAll()) {
            summaries.add(new KnowledgeSourceSummary(
                    root.getSource() == null ? "" : root.getSource().name(),
                    root.getDisplayName(),
                    root.getTotalResources(),
                    root.getScanStatus() == null ? "" : root.getScanStatus().name(),
                    root.isPaused()
            ));
        }
        return summaries;
    }

    private Optional<KnowledgeContextSnippet> toSnippet(
            KnowledgeIndexingService.KnowledgeIndexHit hit,
            String query,
            int passageLength
    ) {
        Optional<KnowledgeResource> resource = resourceRepository.findById(hit.resourceId())
                .filter(candidate -> !candidate.isDeleted());
        if (resource.isEmpty()) {
            return Optional.empty();
        }

        Optional<String> text = conversionText(resource.get().getId());
        if (text.isEmpty()) {
            return Optional.empty();
        }

        KnowledgeRoot root = rootRepository.findById(resource.get().getRootId())
                .orElseGet(KnowledgeRoot::new);
        return Optional.of(toSnippet(
                root,
                resource.get(),
                extractPassage(text.get(), query, passageLength),
                hit.score()
        ));
    }

    private void addJiraIssueSnippets(
            String issueKey,
            int limit,
            int passageLength,
            Set<UUID> seenResources,
            List<KnowledgeContextSnippet> snippets
    ) {
        for (KnowledgeResource resource : resourceRepository.findActiveBySourceAndDisplayNamePrefix(
                KnowledgeSource.JIRA,
                issueKey,
                limit
        )) {
            if (snippets.size() >= limit) {
                return;
            }
            if (seenResources.contains(resource.getId())) {
                continue;
            }
            Optional<String> text = conversionText(resource.getId());
            if (text.isEmpty()) {
                continue;
            }
            KnowledgeRoot root = rootRepository.findById(resource.getRootId()).orElseGet(KnowledgeRoot::new);
            snippets.add(toSnippet(root, resource, extractPassage(text.get(), issueKey, passageLength), Float.MAX_VALUE));
            seenResources.add(resource.getId());
        }
    }

    private Optional<String> conversionText(UUID resourceId) {
        return pipelineRepository.findConversionByResourceId(resourceId)
                .filter(conversion -> Boolean.TRUE.equals(conversion.getSuccess()))
                .map(KnowledgeResourceConversion::getValue)
                .filter(value -> value != null && !value.isBlank());
    }

    private KnowledgeContextSnippet toSnippet(
            KnowledgeRoot root,
            KnowledgeResource resource,
            String content,
            float score
    ) {
        return new KnowledgeContextSnippet(
                root.getSource() == null ? "" : root.getSource().name(),
                root.getDisplayName(),
                resource.getReference(),
                resource.getDisplayName(),
                content,
                score
        );
    }

    /**
     * Pulls a passage of at most {@code maxLength} characters from the resource text, centred on the
     * first place a query term appears and snapped to word boundaries. The whole text is returned when
     * it already fits. This is where "length of results" is applied — at query time, not at ingestion.
     */
    private String extractPassage(String text, String query, int maxLength) {
        String trimmed = text.strip();
        if (maxLength <= 0 || trimmed.length() <= maxLength) {
            return trimmed;
        }
        int match = firstMatchOffset(trimmed, query);
        int start = Math.max(0, match - maxLength / 4);
        int end = Math.min(trimmed.length(), start + maxLength);
        start = Math.max(0, end - maxLength);
        if (start > 0) {
            int nextSpace = trimmed.indexOf(' ', start);
            if (nextSpace >= 0 && nextSpace < end) {
                start = nextSpace + 1;
            }
        }
        if (end < trimmed.length()) {
            int previousSpace = trimmed.lastIndexOf(' ', end);
            if (previousSpace > start) {
                end = previousSpace;
            }
        }
        String passage = trimmed.substring(start, end).strip();
        return (start > 0 ? "… " : "") + passage + (end < trimmed.length() ? " …" : "");
    }

    private int firstMatchOffset(String text, String query) {
        if (query == null || query.isBlank()) {
            return 0;
        }
        String haystack = text.toLowerCase(Locale.ROOT);
        int best = -1;
        for (String token : query.toLowerCase(Locale.ROOT).split("[^\\p{Alnum}]+")) {
            if (token.length() < 2) {
                continue;
            }
            int at = haystack.indexOf(token);
            if (at >= 0 && (best < 0 || at < best)) {
                best = at;
            }
        }
        return Math.max(best, 0);
    }

    private Set<String> issueKeys(String query) {
        if (query == null || query.isBlank()) {
            return Set.of();
        }
        Set<String> keys = new LinkedHashSet<>();
        Matcher matcher = JIRA_ISSUE_KEY.matcher(query);
        while (matcher.find()) {
            keys.add(matcher.group().toUpperCase(java.util.Locale.ROOT));
        }
        return keys;
    }
}
