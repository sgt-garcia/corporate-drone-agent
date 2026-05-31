import { ConversationPanel } from "./ConversationPanel.jsx";
import { ProjectSettingsPanel } from "./ProjectSettingsPanel.jsx";

export function WorkContent({
  activeItem,
  conversationsById,
  draftsByConversationId,
  onDraftChange,
  onProjectSave,
  onSend
}) {
  if (activeItem.type === "empty") {
    return (
      <div className="conversation">
        <div className="empty-greeting" style={{ paddingTop: "12vh" }}>
          <h1 className="ds-h1">Nothing here yet.</h1>
          <p className="ds-body-lg">Create a project to get started.</p>
        </div>
      </div>
    );
  }

  if (activeItem.type === "project") {
    return (
      <ProjectSettingsPanel
        key={activeItem.item.id}
        onSave={onProjectSave}
        project={activeItem.item}
      />
    );
  }

  const conversation = conversationsById[activeItem.item.id] ?? {
    ...activeItem.item,
    projectId: activeItem.project.id,
    messages: []
  };

  return (
    <ConversationPanel
      conversation={conversation}
      key={activeItem.item.id}
      messages={conversation.messages ?? []}
      onDraftChange={(value) => onDraftChange(activeItem.item.id, value)}
      onSend={(content) => onSend(activeItem.item.id, content)}
      project={activeItem.project}
      value={draftsByConversationId[activeItem.item.id] ?? ""}
    />
  );
}
