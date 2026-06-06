import "./styles.css";
import { useEffect, useMemo, useRef, useState } from "react";
import {
  addKnowledgeFolder as addKnowledgeFolderRequest,
  createConversation,
  createProject,
  deleteConversation as deleteConversationRequest,
  deleteProject as deleteProjectRequest,
  getConversation,
  getProjects,
  getSettings,
  pauseKnowledgeFolder as pauseKnowledgeFolderRequest,
  renameConversation as renameConversationRequest,
  removeKnowledgeFolder as removeKnowledgeFolderRequest,
  resumeKnowledgeFolder as resumeKnowledgeFolderRequest,
  saveProject,
  saveSettings,
  scanKnowledgeFolder as scanKnowledgeFolderRequest,
  sendConversationMessage
} from "./api.js";
import { Icon } from "./components/Icon.jsx";
import { Settings } from "./components/Settings.jsx";
import { Sidebar } from "./components/Sidebar.jsx";
import { WorkContent } from "./components/WorkContent.jsx";
import { findWorkItem } from "./data.js";

const defaultProjectInstructions =
  "Use this project context when answering questions about planning, decisions, and follow-up work.";

const defaultKnowledgeFolders = [];

const emptySettings = {
  agentName: "Corporate Drone's Agent",
  aiModel: "none",
  customInstructions:
    "Answer with concise, practical guidance using available local project context first.",
  knowledgeFolders: [],
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
  const [knowledgeFolders, setKnowledgeFolders] = useState(defaultKnowledgeFolders);
  const [activePage, setActivePage] = useState("work");
  const [activeWorkItemId, setActiveWorkItemId] = useState(null);
  const [collapsedProjectIds, setCollapsedProjectIds] = useState([]);
  const [statusText, setStatusText] = useState("Loading...");
  const [menuOpen, setMenuOpen] = useState(false);
  const [renaming, setRenaming] = useState(null);

  // Latest committed projects, so delete handlers can compute a fallback
  // selection without depending on a stale render-time closure.
  const projectsRef = useRef(projects);
  const renameRef = useRef(null);

  useEffect(() => {
    projectsRef.current = projects;
  }, [projects]);

  const activeWorkItem = useMemo(
    () => findWorkItem(projects, activeWorkItemId),
    [activeWorkItemId, projects]
  );

  const breadcrumbProject =
    activeWorkItem.type === "conversation"
      ? activeWorkItem.project?.name
      : activeWorkItem.type === "project"
        ? activeWorkItem.name
        : null;
  const headerTitle =
    activeWorkItem.type === "conversation"
      ? activeWorkItem.name
      : activeWorkItem.type === "project"
        ? "Settings"
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
                conversations: upsertById(project.conversations, conversation, {
                  prepend: true
                })
              }
            : project
        )
      );
    });
    events.addEventListener("conversation-deleted", (event) => {
      const conversation = JSON.parse(event.data);
      removeConversationFromState(conversation.id);
    });
    events.addEventListener("project-deleted", (event) => {
      const { id } = JSON.parse(event.data);
      removeProjectFromState(id);
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
      const updatedSettings = JSON.parse(event.data);
      setSettings(updatedSettings);
      setKnowledgeFolders(updatedSettings.knowledgeFolders ?? []);
    });
    events.onerror = () => setStatusText("Backend event stream disconnected.");

    return () => events.close();
  }, []);

  // Close the conversation header menu on outside click or Escape.
  useEffect(() => {
    if (!menuOpen) {
      return undefined;
    }
    const close = () => setMenuOpen(false);
    const onKey = (event) => {
      if (event.key === "Escape") {
        setMenuOpen(false);
      }
    };
    document.addEventListener("click", close);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("click", close);
      document.removeEventListener("keydown", onKey);
    };
  }, [menuOpen]);

  // Focus and select the field when the rename dialog opens. Keyed on the
  // conversation id (not the whole `renaming` object) so it runs once on open
  // and not on every keystroke — re-running select() each keystroke would
  // re-highlight the field and let the next character overwrite it.
  const renamingId = renaming?.id;
  useEffect(() => {
    if (renamingId && renameRef.current) {
      renameRef.current.focus();
      renameRef.current.select();
    }
  }, [renamingId]);

  async function loadInitialState() {
    try {
      const [loadedProjects, loadedSettings] = await Promise.all([
        getProjects(),
        getSettings()
      ]);
      setProjects(loadedProjects);
      setSettings(loadedSettings);
      setKnowledgeFolders(loadedSettings.knowledgeFolders ?? []);
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
    setProjects((currentProjects) => upsertById(currentProjects, project, { prepend: true }));
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
              conversations: upsertById(
                project.conversations,
                {
                  id: conversation.id,
                  projectId: conversation.projectId,
                  name: conversation.name
                },
                { prepend: true }
              )
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

  // From a project's settings, return to a conversation — the active project's
  // first one, or any available conversation as a fallback.
  function backToWork() {
    const project = activeWorkItem.type === "project" ? activeWorkItem.item : null;
    const target =
      project?.conversations[0]?.id ??
      projects.flatMap((item) => item.conversations)[0]?.id ??
      null;
    if (target) {
      setActivePage("work");
      setActiveWorkItemId(target);
    }
  }

  async function renameConversation(conversationId, name) {
    const trimmed = name.trim();
    if (!trimmed) {
      return;
    }
    try {
      // The conversation-updated event updates the sidebar and header title.
      await renameConversationRequest(conversationId, trimmed);
    } catch (error) {
      setStatusText(error.message);
    }
  }

  async function deleteConversation(conversationId) {
    try {
      await deleteConversationRequest(conversationId);
      removeConversationFromState(conversationId);
    } catch (error) {
      setStatusText(error.message);
    }
  }

  async function deleteProject(projectId) {
    try {
      await deleteProjectRequest(projectId);
      removeProjectFromState(projectId);
    } catch (error) {
      setStatusText(error.message);
    }
  }

  function removeConversationFromState(conversationId) {
    setConversationsById((current) => {
      if (!(conversationId in current)) {
        return current;
      }
      const next = { ...current };
      delete next[conversationId];
      return next;
    });
    setProjects((currentProjects) =>
      currentProjects.map((project) => ({
        ...project,
        conversations: project.conversations.filter((c) => c.id !== conversationId)
      }))
    );
    setActiveWorkItemId((currentId) =>
      currentId === conversationId
        ? fallbackAfterConversationDelete(conversationId)
        : currentId
    );
  }

  function removeProjectFromState(projectId) {
    const projectsNow = projectsRef.current;
    const removed = projectsNow.find((project) => project.id === projectId);
    const removedConversationIds = removed
      ? removed.conversations.map((conversation) => conversation.id)
      : [];

    setConversationsById((current) => {
      if (removedConversationIds.length === 0) {
        return current;
      }
      const next = { ...current };
      removedConversationIds.forEach((id) => delete next[id]);
      return next;
    });
    setProjects((currentProjects) =>
      currentProjects.filter((project) => project.id !== projectId)
    );
    setActiveWorkItemId((currentId) => {
      const activeWasRemoved =
        currentId === projectId || removedConversationIds.includes(currentId);
      if (!activeWasRemoved) {
        return currentId;
      }
      const remainingProjects = projectsNow.filter((project) => project.id !== projectId);
      const firstConversation = remainingProjects.flatMap((project) => project.conversations)[0];
      return firstConversation?.id ?? remainingProjects[0]?.id ?? null;
    });
  }

  function fallbackAfterConversationDelete(conversationId) {
    const projectsNow = projectsRef.current;
    const parent = projectsNow.find((project) =>
      project.conversations.some((conversation) => conversation.id === conversationId)
    );
    if (parent) {
      const sibling = parent.conversations.find(
        (conversation) => conversation.id !== conversationId
      );
      return sibling?.id ?? parent.id;
    }
    const anyConversation = projectsNow
      .flatMap((project) => project.conversations)
      .find((conversation) => conversation.id !== conversationId);
    return anyConversation?.id ?? projectsNow[0]?.id ?? null;
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
    setKnowledgeFolders(savedSettings.knowledgeFolders ?? []);
  }

  async function addKnowledgeFolder(path) {
    try {
      const folder = await addKnowledgeFolderRequest(path);
      setKnowledgeFolders((currentFolders) => upsertById(currentFolders, folder));
      return folder;
    } catch (error) {
      setStatusText(error.message);
      throw error;
    }
  }

  async function removeKnowledgeFolder(folderId) {
    try {
      await removeKnowledgeFolderRequest(folderId);
      setKnowledgeFolders((currentFolders) =>
        currentFolders.filter((folder) => folder.id !== folderId)
      );
    } catch (error) {
      setStatusText(error.message);
      throw error;
    }
  }

  async function scanKnowledgeFolder(folderId) {
    try {
      const folder = await scanKnowledgeFolderRequest(folderId);
      setKnowledgeFolders((currentFolders) => upsertById(currentFolders, folder));
    } catch (error) {
      setStatusText(error.message);
      throw error;
    }
  }

  async function toggleKnowledgeFolderPause(folder) {
    try {
      const savedFolder =
        folder.status === "paused"
          ? await resumeKnowledgeFolderRequest(folder.id)
          : await pauseKnowledgeFolderRequest(folder.id);
      setKnowledgeFolders((currentFolders) => upsertById(currentFolders, savedFolder));
    } catch (error) {
      setStatusText(error.message);
      throw error;
    }
  }

  function addMessageToConversation(conversationId, message) {
    setConversationsById((currentConversations) => {
      const conversation = currentConversations[conversationId];

      if (!conversation) {
        return currentConversations;
      }

      const completesPendingReply = message.role === "assistant" || message.role === "error";
      const messages =
        completesPendingReply
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
          knowledgeFolders={knowledgeFolders}
          onAddKnowledgeFolder={addKnowledgeFolder}
          onRemoveKnowledgeFolder={removeKnowledgeFolder}
          onScanKnowledgeFolder={scanKnowledgeFolder}
          onToggleKnowledgeFolderPause={toggleKnowledgeFolderPause}
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
            {activeWorkItem.type === "project" && (
              <button
                className="btn btn-secondary btn-sm"
                type="button"
                onClick={backToWork}
              >
                <Icon name="arrow-left" size={14} color="var(--gray-700)" />
                Back
              </button>
            )}
            {activeWorkItem.type === "conversation" && (
              <div
                className="header-menu"
                onClick={(event) => event.stopPropagation()}
              >
                <button
                  className="iconbtn"
                  type="button"
                  aria-label="Conversation actions"
                  aria-haspopup="menu"
                  aria-expanded={menuOpen}
                  onClick={() => setMenuOpen((open) => !open)}
                >
                  <Icon name="more-horizontal" size={18} color="var(--gray-500)" />
                </button>
                {menuOpen && (
                  <div className="menu" role="menu">
                    <button
                      className="menuitem"
                      type="button"
                      role="menuitem"
                      onClick={() => {
                        setRenaming({
                          id: activeWorkItem.item.id,
                          name: activeWorkItem.name
                        });
                        setMenuOpen(false);
                      }}
                    >
                      <Icon name="pencil" size={16} color="var(--gray-500)" />
                      Rename
                    </button>
                    <button
                      className="menuitem menuitem-danger"
                      type="button"
                      role="menuitem"
                      onClick={() => {
                        deleteConversation(activeWorkItem.item.id);
                        setMenuOpen(false);
                      }}
                    >
                      <Icon name="trash" size={16} color="var(--danger-600)" />
                      Delete
                    </button>
                  </div>
                )}
              </div>
            )}
          </header>

          {statusText && <div className="app-status">{statusText}</div>}

          <WorkContent
            activeItem={activeWorkItem}
            conversationsById={conversationsById}
            draftsByConversationId={draftsByConversationId}
            onDraftChange={updateDraft}
            onProjectSave={updateProject}
            onProjectDelete={deleteProject}
            onSend={sendMessage}
          />
        </main>
      )}

      {renaming && (
        <div
          className="modal-overlay"
          onClick={() => setRenaming(null)}
        >
          <div
            className="modal"
            role="dialog"
            aria-modal="true"
            aria-label="Rename conversation"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="modal-title">Rename conversation</div>
            <div className="modal-subtitle">
              Give this conversation a clear, descriptive name.
            </div>
            <form
              onSubmit={(event) => {
                event.preventDefault();
                renameConversation(renaming.id, renaming.name);
                setRenaming(null);
              }}
            >
              <input
                ref={renameRef}
                className="input"
                value={renaming.name}
                onChange={(event) =>
                  setRenaming((current) => ({ ...current, name: event.target.value }))
                }
              />
              <div className="modal-actions">
                <button
                  className="btn btn-secondary btn-sm"
                  type="button"
                  onClick={() => setRenaming(null)}
                >
                  Cancel
                </button>
                <button
                  className="btn btn-primary btn-sm"
                  type="submit"
                  disabled={!renaming.name.trim()}
                >
                  Save name
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

function findFirstWorkItemId(projects) {
  const firstProject = projects[0];
  return firstProject?.conversations[0]?.id ?? firstProject?.id ?? null;
}

function upsertById(items, nextItem, { prepend = false } = {}) {
  if (items.some((item) => item.id === nextItem.id)) {
    return items.map((item) => (item.id === nextItem.id ? nextItem : item));
  }
  return prepend ? [nextItem, ...items] : [...items, nextItem];
}
