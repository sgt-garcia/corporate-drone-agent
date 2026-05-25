import "./styles.css";
import { useState } from "react";
import { AddButton } from "./components/IconButtons.jsx";
import { SettingsContent } from "./components/SettingsContent.jsx";
import { WorkContent } from "./components/WorkContent.jsx";
import { WorkMenu } from "./components/WorkMenu.jsx";
import {
  createConversationMessages,
  createInitialMessages,
  findWorkItem,
  initialProjects,
  pages
} from "./data.js";

export default function App() {
  const [projects, setProjects] = useState(initialProjects);
  const [messagesByConversationId, setMessagesByConversationId] = useState(() =>
    createInitialMessages(initialProjects)
  );
  const [draftsByConversationId, setDraftsByConversationId] = useState({});
  const [activePage, setActivePage] = useState("work");
  const [activeWorkItemId, setActiveWorkItemId] = useState(
    initialProjects[0].conversations[0].id
  );
  const [collapsedProjectIds, setCollapsedProjectIds] = useState([]);
  const [activeSettingsItem, setActiveSettingsItem] = useState(pages.settings.menu[0]);

  const page = pages[activePage];
  const activeWorkItem = findWorkItem(projects, activeWorkItemId);
  const activeMenuItem = activePage === "work" ? activeWorkItem.name : activeSettingsItem;
  const pageContext =
    activePage === "work"
      ? activeWorkItem.type === "project"
        ? "Project settings"
        : "Conversation"
      : page.title;
  const showMainHeading = activePage === "work" && activeWorkItem.type === "conversation";

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
    const targetProject = projects.find((project) => project.id === projectId);
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
    setMessagesByConversationId((currentMessages) => ({
      ...currentMessages,
      [conversation.id]: createConversationMessages(
        conversation,
        targetProject?.name ?? "New Project"
      )
    }));
    setActiveWorkItemId(conversation.id);
  }

  function toggleProject(projectId) {
    setCollapsedProjectIds((currentIds) =>
      currentIds.includes(projectId)
        ? currentIds.filter((id) => id !== projectId)
        : [...currentIds, projectId]
    );
  }

  function updateDraft(conversationId, value) {
    setDraftsByConversationId((currentDrafts) => ({
      ...currentDrafts,
      [conversationId]: value
    }));
  }

  function sendMessage(conversationId, content) {
    const trimmedContent = content.trim();

    if (!trimmedContent) {
      return;
    }

    const userMessage = {
      id: `user-${crypto.randomUUID()}`,
      content: trimmedContent,
      createdAt: new Date().toISOString(),
      role: "user"
    };

    setDraftsByConversationId((currentDrafts) => ({
      ...currentDrafts,
      [conversationId]: ""
    }));

    setMessagesByConversationId((currentMessages) => ({
      ...currentMessages,
      [conversationId]: [...(currentMessages[conversationId] ?? []), userMessage]
    }));

    window.setTimeout(() => {
      const statusMessage = {
        id: `status-${crypto.randomUUID()}`,
        content: "CDA is processing...",
        createdAt: new Date().toISOString(),
        role: "status"
      };

      setMessagesByConversationId((currentMessages) => ({
        ...currentMessages,
        [conversationId]: [...(currentMessages[conversationId] ?? []), statusMessage]
      }));

      window.setTimeout(() => {
        const assistantMessage = {
          id: `assistant-${crypto.randomUUID()}`,
          content: `You said:\n\n${trimmedContent}`,
          createdAt: new Date().toISOString(),
          role: "assistant"
        };

        setMessagesByConversationId((currentMessages) => ({
          ...currentMessages,
          [conversationId]: [
            ...(currentMessages[conversationId] ?? []).filter(
              (message) => message.id !== statusMessage.id
            ),
            assistantMessage
          ]
        }));
      }, 1000);
    }, 1000);
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
          {showMainHeading && (
            <section className="page-heading" aria-labelledby="page-title">
              <p>{pageContext}</p>
              <h1 id="page-title">{activeMenuItem}</h1>
            </section>
          )}

          {activePage === "work" ? (
            <WorkContent
              activeItem={activeWorkItem}
              draftsByConversationId={draftsByConversationId}
              messagesByConversationId={messagesByConversationId}
              onDraftChange={updateDraft}
              onSend={sendMessage}
            />
          ) : (
            <SettingsContent activeSettingsItem={activeSettingsItem} />
          )}
        </main>
      </div>
    </div>
  );
}
