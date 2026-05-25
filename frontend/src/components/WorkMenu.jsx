import { AddButton, OverflowButton } from "./IconButtons.jsx";

export function WorkMenu({
  activeItemId,
  collapsedProjectIds,
  onAddConversation,
  onSelect,
  onToggleProject,
  projects
}) {
  return (
    <nav className="project-menu" aria-label="Projects and conversations">
      {projects.map((project) => {
        const isCollapsed = collapsedProjectIds.includes(project.id);

        return (
          <section className="project-group" key={project.id}>
            <div className={project.id === activeItemId ? "project-row active" : "project-row"}>
              <button
                className="collapse-button"
                type="button"
                aria-label={`${isCollapsed ? "Expand" : "Collapse"} ${project.name}`}
                aria-expanded={!isCollapsed}
                onClick={() => onToggleProject(project.id)}
              >
                {isCollapsed ? "\u23f5" : "\u23f7"}
              </button>
              <button
                className="project-button"
                type="button"
                onClick={() => onSelect(project.id)}
              >
                {project.name}
              </button>
              <div className="row-actions">
                <OverflowButton label={`${project.name} actions`} />
                <AddButton
                  label={`Add conversation to ${project.name}`}
                  onClick={() => onAddConversation(project.id)}
                />
              </div>
            </div>
            {!isCollapsed && (
              <div className="conversation-list">
                {project.conversations.map((conversation) => (
                  <div
                    className={
                      conversation.id === activeItemId
                        ? "conversation-row active"
                        : "conversation-row"
                    }
                    key={conversation.id}
                  >
                    <button
                      className="conversation-button"
                      type="button"
                      onClick={() => onSelect(conversation.id)}
                    >
                      {conversation.name}
                    </button>
                    <OverflowButton label={`${conversation.name} actions`} />
                  </div>
                ))}
              </div>
            )}
          </section>
        );
      })}
    </nav>
  );
}
