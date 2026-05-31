import { Icon } from "./Icon.jsx";

export function WorkMenu({
  activeItemId,
  collapsedProjectIds,
  onAddConversation,
  onSelect,
  onToggleProject,
  projects
}) {
  return (
    <nav className="project-tree" aria-label="Projects and conversations">
      {projects.map((project) => {
        const isCollapsed = collapsedProjectIds.includes(project.id);
        const isActiveProject = project.id === activeItemId;

        return (
          <section className="project-group" key={project.id}>
            <div className="project-head">
              <button
                className="tree-toggle"
                type="button"
                aria-label={`${isCollapsed ? "Expand" : "Collapse"} ${project.name}`}
                aria-expanded={!isCollapsed}
                onClick={() => onToggleProject(project.id)}
              >
                <Icon name="chevron-right" size={14} color="var(--gray-400)" />
              </button>
              <button
                className={isActiveProject ? "project-btn active" : "project-btn"}
                type="button"
                onClick={() => onSelect(project.id)}
              >
                <Icon
                  name="folder"
                  size={15}
                  color={isActiveProject ? "var(--blue-600)" : "var(--gray-500)"}
                />
                <span className="project-name">{project.name}</span>
              </button>
              <button
                className="tree-add"
                type="button"
                title="New conversation"
                aria-label={`Add conversation to ${project.name}`}
                onClick={() => onAddConversation(project.id)}
              >
                <Icon name="plus" size={15} color="var(--gray-500)" />
              </button>
            </div>

            {!isCollapsed && (
              <div className="conversation-list">
                {project.conversations.map((conversation) => {
                  const isActive = conversation.id === activeItemId;
                  return (
                    <button
                      className={isActive ? "conversation-btn active" : "conversation-btn"}
                      type="button"
                      key={conversation.id}
                      onClick={() => onSelect(conversation.id)}
                    >
                      <span className="status-dot" aria-hidden="true" />
                      <span className="conversation-name">{conversation.name}</span>
                    </button>
                  );
                })}
              </div>
            )}
          </section>
        );
      })}
    </nav>
  );
}
