import "./styles.css";
import { useState } from "react";

const initialProjects = [
  {
    id: "northwind",
    name: "Northwind Migration",
    conversations: [
      { id: "northwind-kickoff", name: "Kickoff notes" },
      { id: "northwind-inventory", name: "Data inventory" },
      { id: "northwind-risks", name: "Risk review" }
    ]
  },
  {
    id: "atlas",
    name: "Atlas Reporting",
    conversations: [
      { id: "atlas-dashboard", name: "Dashboard scope" },
      { id: "atlas-finance", name: "Finance metrics" },
      { id: "atlas-release", name: "Release checklist" }
    ]
  },
  {
    id: "helix",
    name: "Helix Operations",
    conversations: [
      { id: "helix-status", name: "Weekly status" },
      { id: "helix-vendor", name: "Vendor follow-up" },
      { id: "helix-incident", name: "Incident summary" }
    ]
  }
];

const pages = {
  work: {
    title: "Work"
  },
  settings: {
    title: "Settings",
    menu: ["General", "Models", "Connectors", "Storage"]
  }
};

export default function App() {
  const [projects, setProjects] = useState(initialProjects);
  const [activePage, setActivePage] = useState("work");
  const [activeWorkItemId, setActiveWorkItemId] = useState(
    initialProjects[0].conversations[0].id
  );
  const [collapsedProjectIds, setCollapsedProjectIds] = useState([]);
  const [activeSettingsItem, setActiveSettingsItem] = useState(pages.settings.menu[0]);

  const page = pages[activePage];
  const activeWorkItem = findWorkItem(projects, activeWorkItemId);
  const activeMenuItem = activePage === "work" ? activeWorkItem.name : activeSettingsItem;

  function addProject() {
    const project = {
      id: `project-${crypto.randomUUID()}`,
      name: "New Project",
      conversations: []
    };

    setProjects((currentProjects) => [...currentProjects, project]);
    setActiveWorkItemId(project.id);
  }

  function addConversation(projectId) {
    const conversation = {
      id: `conversation-${crypto.randomUUID()}`,
      name: "New Conversation"
    };

    setProjects((currentProjects) =>
      currentProjects.map((project) =>
        project.id === projectId
          ? {
              ...project,
              conversations: [...project.conversations, conversation]
            }
          : project
      )
    );
    setActiveWorkItemId(conversation.id);
  }

  function toggleProject(projectId) {
    setCollapsedProjectIds((currentIds) =>
      currentIds.includes(projectId)
        ? currentIds.filter((id) => id !== projectId)
        : [...currentIds, projectId]
    );
  }

  return (
    <div className="app-shell">
      <header className="top-menu">
        <div className="brand">{"\u2728 CDA 0.0.1"}</div>
        <nav className="primary-nav" aria-label="Primary">
          <button
            className={activePage === "work" ? "nav-button active" : "nav-button"}
            type="button"
            onClick={() => setActivePage("work")}
          >
            Work
          </button>
          <button
            className={activePage === "settings" ? "nav-button active" : "nav-button"}
            type="button"
            onClick={() => setActivePage("settings")}
          >
            Settings
          </button>
          <a
            className="nav-button"
            href="https://www.corporatedroneagent.ai"
            target="_blank"
            rel="noreferrer"
          >
            Support
          </a>
        </nav>
      </header>

      <div className="workspace">
        <aside className="side-menu" aria-label={`${page.title} menu`}>
          {activePage === "work" ? (
            <div className="side-menu-title action-row">
              <span>{page.title}</span>
              <div className="row-actions">
                <AddButton label="Add project" onClick={addProject} />
              </div>
            </div>
          ) : (
            <div className="side-menu-title">{page.title}</div>
          )}
          {activePage === "work" ? (
            <WorkMenu
              activeItemId={activeWorkItemId}
              collapsedProjectIds={collapsedProjectIds}
              onAddConversation={addConversation}
              onSelect={setActiveWorkItemId}
              onToggleProject={toggleProject}
              projects={projects}
            />
          ) : (
            <nav className="side-menu-items">
              {page.menu.map((item) => (
                <button
                  className={item === activeMenuItem ? "side-button active" : "side-button"}
                  key={item}
                  type="button"
                  onClick={() => setActiveSettingsItem(item)}
                >
                  {item}
                </button>
              ))}
            </nav>
          )}
        </aside>

        <main className="main-body">
          <section className="page-heading" aria-labelledby="page-title">
            <p>{page.title}</p>
            <h1 id="page-title">{activeMenuItem}</h1>
          </section>

          {activePage === "work" ? <WorkPanel projects={projects} /> : <SettingsPanel />}
        </main>
      </div>
    </div>
  );
}

function findWorkItem(projects, activeId) {
  for (const project of projects) {
    if (project.id === activeId) {
      return project;
    }

    const conversation = project.conversations.find((item) => item.id === activeId);
    if (conversation) {
      return conversation;
    }
  }

  return projects[0] ?? { name: "Work" };
}

function WorkMenu({
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
              {isCollapsed ? "\u203A" : "\u2304"}
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

function OverflowButton({ label }) {
  return (
    <button className="overflow-button" type="button" aria-label={label}>
      {"\u2026"}
    </button>
  );
}

function AddButton({ label, onClick }) {
  return (
    <button className="icon-button" type="button" aria-label={label} onClick={onClick}>
      +
    </button>
  );
}

function WorkPanel({ projects }) {
  const conversationCount = projects.reduce(
    (total, project) => total + project.conversations.length,
    0
  );

  return (
    <section className="content-grid" aria-label="Work overview">
      <article className="summary-card wide">
        <span className="card-label">Current focus</span>
        <h2>Personal workspace</h2>
        <p>
          Keep project conversations, scheduled follow-ups, and indexed sources in
          one calm command center.
        </p>
      </article>
      <article className="summary-card">
        <span className="card-label">Projects</span>
        <strong>{projects.length}</strong>
      </article>
      <article className="summary-card">
        <span className="card-label">Conversations</span>
        <strong>{conversationCount}</strong>
      </article>
    </section>
  );
}

function SettingsPanel() {
  return (
    <section className="settings-panel" aria-label="Settings">
      <label>
        Workspace name
        <input type="text" defaultValue="Corporate Drone Agent" />
      </label>
      <label>
        Default model provider
        <select defaultValue="local">
          <option value="local">Local</option>
          <option value="openai">OpenAI</option>
          <option value="azure">Azure OpenAI</option>
        </select>
      </label>
      <label className="toggle-row">
        <span>Local-first indexing</span>
        <input type="checkbox" defaultChecked />
      </label>
    </section>
  );
}
