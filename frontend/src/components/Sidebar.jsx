import { Icon } from "./Icon.jsx";
import { Logomark } from "./Logomark.jsx";
import { WorkMenu } from "./WorkMenu.jsx";

export function Sidebar({
  projects,
  activeWorkItemId,
  collapsedProjectIds,
  onAddProject,
  onAddConversation,
  onSelectWorkItem,
  onToggleProject,
  onOpenSettings,
  settingsActive
}) {
  return (
    <aside className="sidebar" aria-label="Projects and settings">
      <div className="sidebar-brand">
        <Logomark size={30} />
        <span className="brand-name">
          Corporate Drone&rsquo;s <span className="brand-accent">Agent</span>
        </span>
      </div>

      <div className="sidebar-section-head">
        <span className="ds-overline">Projects</span>
        <button
          className="tree-add"
          type="button"
          title="New project"
          aria-label="New project"
          onClick={onAddProject}
        >
          <Icon name="plus" size={15} color="var(--gray-500)" />
        </button>
      </div>

      <div className="sidebar-tree">
        <WorkMenu
          activeItemId={activeWorkItemId}
          collapsedProjectIds={collapsedProjectIds}
          onAddConversation={onAddConversation}
          onSelect={onSelectWorkItem}
          onToggleProject={onToggleProject}
          projects={projects}
        />
      </div>

      <div className="sidebar-footer">
        <button
          className={settingsActive ? "settings-entry active" : "settings-entry"}
          type="button"
          aria-current={settingsActive ? "page" : undefined}
          onClick={onOpenSettings}
        >
          <Icon
            name="settings"
            size={17}
            color={settingsActive ? "var(--blue-600)" : "var(--gray-500)"}
          />
          Settings
        </button>
        <a
          className="sidebar-help"
          href="https://www.corporatedroneagent.ai"
          target="_blank"
          rel="noopener noreferrer"
          title="Help"
          aria-label="Help"
        >
          <Icon name="help-circle" size={17} color="var(--gray-500)" />
        </a>
      </div>
    </aside>
  );
}
