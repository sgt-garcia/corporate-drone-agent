package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.model.Conversation;
import ai.corporatedroneagent.model.Project;
import ai.corporatedroneagent.tools.KnowledgeSearchTools;
import java.util.List;

record ChatRequest(
        ApplicationSettings settings,
        Conversation conversation,
        Project project,
        List<KnowledgeContextSnippet> knowledgeContext,
        // The on-demand search tool, present only when the search mode is enabled.
        KnowledgeSearchTools knowledgeSearchTools
) {
}
