import { ConversationPanel } from "./ConversationPanel.jsx";
import { Icon } from "./Icon.jsx";
import { ProjectSettingsPanel } from "./ProjectSettingsPanel.jsx";

export function WorkContent({
  activeItem,
  conversationsById,
  draftsByConversationId,
  echoMode,
  onCreateProject,
  onDraftChange,
  onOpenProviders,
  onProjectSave,
  onProjectDelete,
  onRetry,
  onSend
}) {
  if (activeItem.type === "empty") {
    return (
      <div className="no-projects">
        <div className="no-projects-inner">
          <span className="no-projects-icon">
            <Icon name="folder" size={26} color="var(--blue-600)" />
          </span>
          <h1 className="ds-h3">Nothing here yet</h1>
          <p className="ds-body">
            Create a project to get started. Projects keep related conversations,
            context, and folders together.
          </p>
          <button className="btn btn-primary" type="button" onClick={onCreateProject}>
            <Icon name="plus" size={16} color="#fff" /> Create a project
          </button>
        </div>
      </div>
    );
  }

  if (activeItem.type === "project") {
    return (
      <ProjectSettingsPanel
        key={activeItem.item.id}
        onSave={onProjectSave}
        onDelete={onProjectDelete}
        project={activeItem.item}
      />
    );
  }

  const loadedConversation = conversationsById[activeItem.item.id];
  const conversation = loadedConversation ?? {
    ...activeItem.item,
    projectId: activeItem.project.id,
    messages: []
  };

  return (
    <ConversationPanel
      conversation={conversation}
      key={activeItem.item.id}
      echoMode={echoMode}
      isLoaded={Boolean(loadedConversation)}
      messages={conversation.messages ?? []}
      onDraftChange={(value) => onDraftChange(activeItem.item.id, value)}
      onOpenProviders={onOpenProviders}
      onRetry={() => onRetry(activeItem.item.id)}
      onSend={(content) => onSend(activeItem.item.id, content)}
      project={activeItem.project}
      value={draftsByConversationId[activeItem.item.id] ?? ""}
    />
  );
}
