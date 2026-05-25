import { ConversationPanel } from "./ConversationPanel.jsx";
import { ProjectSettingsPanel } from "./ProjectSettingsPanel.jsx";

export function WorkContent({
  activeItem,
  conversationsById,
  draftsByConversationId,
  onConversationReload,
  onConversationSave,
  onDraftChange,
  onProjectSave,
  onSend
}) {
  if (activeItem.type === "empty") {
    return <div className="empty-state">Create a project to get started.</div>;
  }

  if (activeItem.type === "project") {
    return (
      <ProjectSettingsPanel
        key={activeItem.item.id}
        onReload={() => {}}
        onSave={onProjectSave}
        project={activeItem.item}
      />
    );
  }

  const conversation = conversationsById[activeItem.item.id] ?? {
    ...activeItem.item,
    projectId: activeItem.project.id,
    settings: {
      customInstructions: ""
    },
    messages: []
  };

  return (
    <ConversationPanel
      conversation={conversation}
      key={activeItem.item.id}
      messages={conversation.messages ?? []}
      onDraftChange={(value) => onDraftChange(activeItem.item.id, value)}
      onReload={() => onConversationReload(activeItem.item.id)}
      onSave={onConversationSave}
      onSend={(content) => onSend(activeItem.item.id, content)}
      project={activeItem.project}
      value={draftsByConversationId[activeItem.item.id] ?? ""}
    />
  );
}
