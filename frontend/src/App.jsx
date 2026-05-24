import "./styles.css";
import { useState } from "react";

const pages = {
  work: {
    title: "Work",
    menu: ["Dashboard", "Projects", "Conversations", "Scheduled jobs"]
  },
  settings: {
    title: "Settings",
    menu: ["General", "Models", "Connectors", "Storage"]
  }
};

export default function App() {
  const [activePage, setActivePage] = useState("work");
  const [activeMenuItem, setActiveMenuItem] = useState(pages.work.menu[0]);

  function openPage(page) {
    setActivePage(page);
    setActiveMenuItem(pages[page].menu[0]);
  }

  const page = pages[activePage];

  return (
    <div className="app-shell">
      <header className="top-menu">
        <div className="brand">✨ CDA 0.0.1</div>
        <nav className="primary-nav" aria-label="Primary">
          <button
            className={activePage === "work" ? "nav-button active" : "nav-button"}
            type="button"
            onClick={() => openPage("work")}
          >
            Work
          </button>
          <button
            className={activePage === "settings" ? "nav-button active" : "nav-button"}
            type="button"
            onClick={() => openPage("settings")}
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
          <div className="side-menu-title">{page.title}</div>
          <nav className="side-menu-items">
            {page.menu.map((item) => (
              <button
                className={item === activeMenuItem ? "side-button active" : "side-button"}
                key={item}
                type="button"
                onClick={() => setActiveMenuItem(item)}
              >
                {item}
              </button>
            ))}
          </nav>
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

function WorkPanel() {
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
        <strong>0</strong>
      </article>
      <article className="summary-card">
        <span className="card-label">Scheduled jobs</span>
        <strong>0</strong>
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
