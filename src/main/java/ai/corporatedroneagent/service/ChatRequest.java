package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.model.Conversation;
import ai.corporatedroneagent.model.Project;
import java.util.List;

record ChatRequest(
        ApplicationSettings settings,
        Conversation conversation,
        Project project,
        List<KnowledgeContextSnippet> knowledgeContext
) {
}
