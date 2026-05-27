import "./styles.css";
import { useEffect, useMemo, useState } from "react";
import {
  createConversation,
  createProject,
  getConversation,
  getProjects,
  getSettings,
  saveProject,
  saveSettings,
  sendConversationMessage
} from "./api.js";
import { AddButton } from "./components/IconButtons.jsx";
import { SettingsContent } from "./components/SettingsContent.jsx";
import { WorkContent } from "./components/WorkContent.jsx";
import { WorkMenu } from "./components/WorkMenu.jsx";
import { findWorkItem, pages } from "./data.js";

const defaultProjectInstructions =
  "Use this project context when answering questions about planning, decisions, and follow-up work.";

const emptySettings = {
  agentName: "Corporate Drone Agent",
  aiModel: "none",
  customInstructions:
    "Answer with concise, practical guidance using available local project context first.",
  openAi: {
    apiKey: "",
    model: "gpt-4.1-mini"
  },
  openAiOfficial: {
    apiKey: "",
    model: "gpt-5-mini"
  },
  azureOpenAi: {
    endpoint: "",
    apiKey: "",
    deploymentName: ""
  }
};

export default function App() {
  const [projects, setProjects] = useState([]);
  const [conversationsById, setConversationsById] = useState({});
  const [draftsByConversationId, setDraftsByConversationId] = useState({});
  const [settings, setSettings] = useState(emptySettings);
  const [activePage, setActivePage] = useState("work");
  const [activeWorkItemId, setActiveWorkItemId] = useState(null);
  const [collapsedProjectIds, setCollapsedProjectIds] = useState([]);
  const [activeSettingsItem, setActiveSettingsItem] = useState(pages.settings.menu[0]);
  const [statusText, setStatusText] = useState("Loading...");

  const page = pages[activePage];
  const activeWorkItem = useMemo(
    () => findWorkItem(projects, activeWorkItemId),
    [activeWorkItemId, projects]
  );
  const activeMenuItem =
    activePage === "work" ? activeWorkItem.name : activeSettingsItem;
  const pageContext =
    activePage === "work"
      ? activeWorkItem.type === "project"
        ? "Project settings"
        : "Conversation"
      : page.title;
  const showMainHeading =
    activePage === "work" && activeWorkItem.type === "conversation";

  useEffect(() => {
    loadInitialState();
  }, []);

  useEffect(() => {
    if (activeWorkItem.type !== "conversation") {
      return;
    }

    loadConversation(activeWorkItem.item.id);
  }, [activeWorkItem]);

  useEffect(() => {
    const events = new EventSource("/api/events");

    events.addEventListener("connected", () => setStatusText(""));
    events.addEventListener("message-created", (event) => {
      const payload = JSON.parse(event.data);
      addMessageToConversation(payload.conversationId, payload.message);
    });
    events.addEventListener("projects-updated", (event) => {
      setProjects(JSON.parse(event.data));
    });
    events.addEventListener("project-updated", (event) => {
      const project = JSON.parse(event.data);
      setProjects((currentProjects) =>
        currentProjects.map((item) => (item.id === project.id ? project : item))
      );
    });
    events.addEventListener("conversation-created", (event) => {
      const conversation = JSON.parse(event.data);
      setProjects((currentProjects) =>
        currentProjects.map((project) =>
          project.id === conversation.projectId
            ? {
                ...project,
                conversations: upsertById(project.conversations, conversation)
              }
            : project
        )
      );
    });
    events.addEventListener("conversation-updated", (event) => {
      const conversation = JSON.parse(event.data);
      setConversationsById((currentConversations) => ({
        ...currentConversations,
        [conversation.id]: conversation
      }));
      setProjects((currentProjects) =>
        currentProjects.map((project) =>
          project.id === conversation.projectId
            ? {
                ...project,
                conversations: upsertById(project.conversations, {
                  id: conversation.id,
                  projectId: conversation.projectId,
                  name: conversation.name
                })
              }
            : project
        )
      );
    });
    events.addEventListener("settings-updated", (event) => {
      setSettings(JSON.parse(event.data));
    });
    events.onerror = () => setStatusText("Backend event stream disconnected.");

    return () => events.close();
  }, []);

  async function loadInitialState() {
    try {
      const [loadedProjects, loadedSettings] = await Promise.all([
        getProjects(),
        getSettings()
      ]);
      setProjects(loadedProjects);
      setSettings(loadedSettings);
      setActiveWorkItemId((currentId) => currentId ?? findFirstWorkItemId(loadedProjects));
      setStatusText("");
    } catch (error) {
      setStatusText(error.message);
    }
  }

  async function loadConversation(conversationId) {
    if (conversationsById[conversationId]) {
      return;
    }

    try {
      const conversation = await getConversation(conversationId);
      setConversationsById((currentConversations) => ({
        ...currentConversations,
        [conversation.id]: conversation
      }));
    } catch (error) {
      setStatusText(error.message);
    }
  }

  async function addProject() {
    const project = await createProject({
      name: "New Project",
      workingFolder: "",
      customInstructions: defaultProjectInstructions
    });
    setProjects((currentProjects) => upsertById(currentProjects, project));
    setActiveWorkItemId(project.id);
  }

  async function addConversation(projectId) {
    const conversation = await createConversation(projectId, {
      name: "New Conversation"
    });

    setConversationsById((currentConversations) => ({
      ...currentConversations,
      [conversation.id]: conversation
    }));
    setProjects((currentProjects) =>
      currentProjects.map((project) =>
        project.id === projectId
          ? {
              ...project,
              conversations: upsertById(project.conversations, {
                id: conversation.id,
                projectId: conversation.projectId,
                name: conversation.name
              })
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

  function updateDraft(conversationId, value) {
    setDraftsByConversationId((currentDrafts) => ({
      ...currentDrafts,
      [conversationId]: value
    }));
  }

  async function sendMessage(conversationId, content) {
    const trimmedContent = content.trim();

    if (!trimmedContent) {
      return;
    }

    setDraftsByConversationId((currentDrafts) => ({
      ...currentDrafts,
      [conversationId]: ""
    }));

    try {
      const message = await sendConversationMessage(conversationId, trimmedContent);
      addMessageToConversation(conversationId, message);
    } catch (error) {
      setStatusText(error.message);
    }
  }

  async function updateProject(project) {
    const savedProject = await saveProject(project);
    setProjects((currentProjects) => upsertById(currentProjects, savedProject));
  }

  async function updateSettings(nextSettings) {
    const savedSettings = await saveSettings(nextSettings);
    setSettings(savedSettings);
  }

  function addMessageToConversation(conversationId, message) {
    setConversationsById((currentConversations) => {
      const conversation = currentConversations[conversationId];

      if (!conversation) {
        return currentConversations;
      }

      const messages =
        message.role === "assistant"
          ? conversation.messages.filter((item) => item.role !== "status")
          : conversation.messages;

      return {
        ...currentConversations,
        [conversationId]: {
          ...conversation,
          messages: upsertById(messages, message)
        }
      };
    });
  }

  return (
    <div className="app-shell">
      <header className="top-menu">
        <div className="brand">CDA 0.0.1</div>
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
          {statusText && <div className="app-status">{statusText}</div>}

          {showMainHeading && (
            <section className="page-heading" aria-labelledby="page-title">
              <p>{pageContext}</p>
              <h1 id="page-title">{activeMenuItem}</h1>
            </section>
          )}

          {activePage === "work" ? (
            <WorkContent
              activeItem={activeWorkItem}
              conversationsById={conversationsById}
              draftsByConversationId={draftsByConversationId}
              onDraftChange={updateDraft}
              onProjectSave={updateProject}
              onSend={sendMessage}
            />
          ) : (
            <SettingsContent
              activeSettingsItem={activeSettingsItem}
              onReload={loadInitialState}
              onSave={updateSettings}
              settings={settings}
            />
          )}
        </main>
      </div>
    </div>
  );
}

function findFirstWorkItemId(projects) {
  const firstProject = projects[0];
  return firstProject?.conversations[0]?.id ?? firstProject?.id ?? null;
}

function upsertById(items, nextItem) {
  return items.some((item) => item.id === nextItem.id)
    ? items.map((item) => (item.id === nextItem.id ? nextItem : item))
    : [...items, nextItem];
}
