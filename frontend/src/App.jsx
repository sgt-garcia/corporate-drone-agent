import "./styles.css";
import { useState } from "react";

const projects = [
  {
    id: "northwind",
    name: "Northwind Migration",
    conversations: ["Kickoff notes", "Data inventory", "Risk review"]
  },
  {
    id: "atlas",
    name: "Atlas Reporting",
    conversations: ["Dashboard scope", "Finance metrics", "Release checklist"]
  },
  {
    id: "helix",
    name: "Helix Operations",
    conversations: ["Weekly status", "Vendor follow-up", "Incident summary"]
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
  const [activePage, setActivePage] = useState("work");
  const [activeWorkItem, setActiveWorkItem] = useState(projects[0].conversations[0]);
  const [activeSettingsItem, setActiveSettingsItem] = useState(pages.settings.menu[0]);

  const page = pages[activePage];
  const activeMenuItem = activePage === "work" ? activeWorkItem : activeSettingsItem;

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
                <OverflowButton label="Work actions" />
                <AddButton label="Add work item" />
              </div>
            </div>
          ) : (
            <div className="side-menu-title">{page.title}</div>
          )}
          {activePage === "work" ? (
            <WorkMenu activeItem={activeWorkItem} onSelect={setActiveWorkItem} />
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

          {activePage === "work" ? <WorkPanel /> : <SettingsPanel />}
        </main>
      </div>
    </div>
  );
}

function WorkMenu({ activeItem, onSelect }) {
  return (
    <nav className="project-menu" aria-label="Projects and conversations">
      {projects.map((project) => (
        <section className="project-group" key={project.id}>
          <div className={project.name === activeItem ? "project-row active" : "project-row"}>
            <button
              className="project-button"
              type="button"
              onClick={() => onSelect(project.name)}
            >
              {project.name}
            </button>
            <div className="row-actions">
              <OverflowButton label={`${project.name} actions`} />
              <AddButton label={`Add conversation to ${project.name}`} />
            </div>
          </div>
          <div className="conversation-list">
            {project.conversations.map((conversation) => (
              <div
                className={
                  conversation === activeItem
                    ? "conversation-row active"
                    : "conversation-row"
                }
                key={conversation}
              >
                <button
                  className="conversation-button"
                  type="button"
                  onClick={() => onSelect(conversation)}
                >
                  {conversation}
                </button>
                <OverflowButton label={`${conversation} actions`} />
              </div>
            ))}
          </div>
        </section>
      ))}
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

function AddButton({ label }) {
  return (
    <button className="icon-button" type="button" aria-label={label}>
      +
    </button>
  );
}

function WorkPanel() {
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
