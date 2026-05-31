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
import { Icon } from "./components/Icon.jsx";
import { Settings } from "./components/Settings.jsx";
import { Sidebar } from "./components/Sidebar.jsx";
import { WorkContent } from "./components/WorkContent.jsx";
import { findWorkItem } from "./data.js";

const defaultProjectInstructions =
  "Use this project context when answering questions about planning, decisions, and follow-up work.";

const emptySettings = {
  agentName: "Corporate Drone Agent",
  aiModel: "none",
  customInstructions:
    "Answer with concise, practical guidance using available local project context first.",
  openAi: { apiKey: "", apiKeyConfigured: false, apiKeyLastFour: "", model: "" },
  openAiSdk: { apiKey: "", apiKeyConfigured: false, apiKeyLastFour: "", model: "" },
  azureOpenAi: {
    endpoint: "",
    apiKey: "",
    apiKeyConfigured: false,
    apiKeyLastFour: "",
    deploymentName: ""
  },
  ollama: { baseUrl: "http://localhost:11434", model: "" },
  mistral: { apiKey: "", apiKeyConfigured: false, apiKeyLastFour: "", model: "" },
  gemini: { apiKey: "", apiKeyConfigured: false, apiKeyLastFour: "", model: "" },
  anthropic: { apiKey: "", apiKeyConfigured: false, apiKeyLastFour: "", model: "" },
  groq: { apiKey: "", apiKeyConfigured: false, apiKeyLastFour: "", model: "" },
  deepSeek: { apiKey: "", apiKeyConfigured: false, apiKeyLastFour: "", model: "" }
};

export default function App() {
  const [projects, setProjects] = useState([]);
  const [conversationsById, setConversationsById] = useState({});
  const [draftsByConversationId, setDraftsByConversationId] = useState({});
  const [settings, setSettings] = useState(emptySettings);
  const [activePage, setActivePage] = useState("work");
  const [activeWorkItemId, setActiveWorkItemId] = useState(null);
  const [collapsedProjectIds, setCollapsedProjectIds] = useState([]);
  const [statusText, setStatusText] = useState("Loading...");

  const activeWorkItem = useMemo(
    () => findWorkItem(projects, activeWorkItemId),
    [activeWorkItemId, projects]
  );

  const breadcrumbProject =
    activeWorkItem.type === "conversation" ? activeWorkItem.project?.name : null;
  const headerTitle =
    activeWorkItem.type === "conversation"
      ? activeWorkItem.name
      : activeWorkItem.type === "project"
        ? "Project settings"
        : "Workspace";

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
    setActivePage("work");
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
    setCollapsedProjectIds((currentIds) => currentIds.filter((id) => id !== projectId));
    setActivePage("work");
    setActiveWorkItemId(conversation.id);
  }

  function selectWorkItem(id) {
    setActivePage("work");
    setActiveWorkItemId(id);
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
      <Sidebar
        projects={projects}
        activeWorkItemId={activePage === "work" ? activeWorkItemId : null}
        collapsedProjectIds={collapsedProjectIds}
        onAddProject={addProject}
        onAddConversation={addConversation}
        onSelectWorkItem={selectWorkItem}
        onToggleProject={toggleProject}
        onOpenSettings={() => setActivePage("settings")}
        settingsActive={activePage === "settings"}
      />

      {activePage === "settings" ? (
        <Settings
          onClose={() => setActivePage("work")}
          settings={settings}
          onSave={updateSettings}
        />
      ) : (
        <main className="main">
          <header className="main-header">
            <div className="breadcrumb">
              {breadcrumbProject && (
                <>
                  <span className="breadcrumb-project">{breadcrumbProject}</span>
                  <Icon name="chevron-right" size={14} color="var(--gray-300)" />
                </>
              )}
              <span className="breadcrumb-title">{headerTitle}</span>
            </div>
            <button className="iconbtn" type="button" aria-label="More actions">
              <Icon name="more-horizontal" size={18} color="var(--gray-500)" />
            </button>
          </header>

          {statusText && <div className="app-status">{statusText}</div>}

          <WorkContent
            activeItem={activeWorkItem}
            conversationsById={conversationsById}
            draftsByConversationId={draftsByConversationId}
            onDraftChange={updateDraft}
            onProjectSave={updateProject}
            onSend={sendMessage}
          />
        </main>
      )}
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
