import { ConversationPanel } from "./ConversationPanel.jsx";
import { ProjectSettingsPanel } from "./ProjectSettingsPanel.jsx";

export function WorkContent({
  activeItem,
  draftsByConversationId,
  messagesByConversationId,
  onDraftChange,
  onSend
}) {
  if (activeItem.type === "project") {
    return <ProjectSettingsPanel key={activeItem.item.id} project={activeItem.item} />;
  }

  return (
    <ConversationPanel
      conversation={activeItem.item}
      key={activeItem.item.id}
      messages={messagesByConversationId[activeItem.item.id] ?? []}
      onDraftChange={(value) => onDraftChange(activeItem.item.id, value)}
      onSend={(content) => onSend(activeItem.item.id, content)}
      project={activeItem.project}
      value={draftsByConversationId[activeItem.item.id] ?? ""}
    />
  );
}
