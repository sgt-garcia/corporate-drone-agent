import "./styles.css";
import { useEffect, useMemo, useRef, useState } from "react";
import {
  addConfluenceSpace as addConfluenceSpaceRequest,
  addJiraProject as addJiraProjectRequest,
  addKnowledgeFolder as addKnowledgeFolderRequest,
  clearConfluenceConnection as clearConfluenceConnectionRequest,
  clearJiraConnection as clearJiraConnectionRequest,
  createConversation,
  createProject,
  deleteConversation as deleteConversationRequest,
  deleteProject as deleteProjectRequest,
  getConversation,
  getProjects,
  getSettings,
  markConversationSeen as markConversationSeenRequest,
  pauseConfluenceSpace as pauseConfluenceSpaceRequest,
  pauseJiraProject as pauseJiraProjectRequest,
  pauseKnowledgeFolder as pauseKnowledgeFolderRequest,
  regenerateConversationReply,
  renameConversation as renameConversationRequest,
  removeConfluenceSpace as removeConfluenceSpaceRequest,
  removeJiraProject as removeJiraProjectRequest,
  removeKnowledgeFolder as removeKnowledgeFolderRequest,
  resumeConfluenceSpace as resumeConfluenceSpaceRequest,
  resumeJiraProject as resumeJiraProjectRequest,
  resumeKnowledgeFolder as resumeKnowledgeFolderRequest,
  retryConversationReply,
  saveProject,
  saveSettings,
  saveConfluenceConnection as saveConfluenceConnectionRequest,
  saveJiraConnection as saveJiraConnectionRequest,
  scanConfluenceSpace as scanConfluenceSpaceRequest,
  scanJiraProject as scanJiraProjectRequest,
  scanKnowledgeFolder as scanKnowledgeFolderRequest,
  searchConfluenceSpaces as searchConfluenceSpacesRequest,
  searchJiraProjects as searchJiraProjectsRequest,
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
  agentName: "Corporate Drone's Agent",
  aiModel: "none",
  customInstructions:
    "Answer with concise, practical guidance using available local project context first.",
  filesystemToolEnabled: true,
  knowledgeTool: {
    auto: { enabled: true, results: 10, length: 3000 },
    search: { enabled: false, results: 10, length: 3000 }
  },
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
  bedrock: {
    region: "us-east-1",
    accessKey: "",
    accessKeyConfigured: false,
    accessKeyLastFour: "",
    clearAccessKey: false,
    secretKey: "",
    secretKeyConfigured: false,
    clearSecretKey: false,
    model: ""
  },
  ollama: { baseUrl: "http://localhost:11434", model: "" },
  mistral: { apiKey: "", apiKeyConfigured: false, apiKeyLastFour: "", model: "" },
  gemini: { apiKey: "", apiKeyConfigured: false, apiKeyLastFour: "", model: "" },
  anthropic: { apiKey: "", apiKeyConfigured: false, apiKeyLastFour: "", model: "" },
  groq: { apiKey: "", apiKeyConfigured: false, apiKeyLastFour: "", model: "" },
  deepSeek: { apiKey: "", apiKeyConfigured: false, apiKeyLastFour: "", model: "" },
  jira: {
    instanceUrl: "",
    email: "",
    connected: false,
    apiVersion: "3",
    tokenConfigured: false,
    tokenLastFour: "",
    tokenExpiresDays: null,
    projects: []
  },
  confluence: {
    instanceUrl: "",
    email: "",
    connected: false,
    tokenConfigured: false,
    tokenLastFour: "",
    tokenExpiresDays: null,
    spaces: []
  }
};

export default function App() {
  const [projects, setProjects] = useState([]);
  const [conversationsById, setConversationsById] = useState({});
  const [draftsByConversationId, setDraftsByConversationId] = useState({});
  const [settings, setSettings] = useState(emptySettings);
  const [knowledgeFolders, setKnowledgeFolders] = useState([]);
  // Live per-source scan progress: source id -> array of recent file names /
  // ticket keys, streamed over SSE while a folder or Jira project is scanning.
  const [scanProgressById, setScanProgressById] = useState({});
  const [activePage, setActivePage] = useState("work");
  const [settingsSection, setSettingsSection] = useState(null);
  const [activeWorkItemId, setActiveWorkItemId] = useState(null);
  const [collapsedProjectIds, setCollapsedProjectIds] = useState([]);
  const [statusText, setStatusText] = useState("Loading...");
  const [menuOpen, setMenuOpen] = useState(false);
  const [renaming, setRenaming] = useState(null);

  // Latest committed projects, so delete handlers can compute a fallback
  // selection without depending on a stale render-time closure.
  const projectsRef = useRef(projects);
  const renameRef = useRef(null);
  // Conversations with a retry/regenerate POST in flight. Set synchronously on
  // click and held until the server's status turn lands (or the POST fails), so a
  // second click before the "thinking" turn appears can't fire a duplicate POST —
  // the !busy button gate alone leaves that first-click window open.
  const pendingReplyActionsRef = useRef(new Set());

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

  // Primitive id/status of the open conversation. findWorkItem rebuilds
  // activeWorkItem as a fresh object on every render, so the open-conversation
  // effect below must gate on these primitives rather than the object — otherwise
  // any projects mutation (e.g. a sibling conversation's status SSE) re-runs it.
  const activeConversationId =
    activeWorkItem.type === "conversation" ? activeWorkItem.item.id : null;
  const activeConversationStatus =
    activeWorkItem.type === "conversation" ? activeWorkItem.item.status : null;

  useEffect(() => {
    loadInitialState();
  }, []);

  useEffect(() => {
    if (!activeConversationId) {
      return;
    }

    loadConversation(activeConversationId);
    // Opening a conversation that just finished acknowledges it (review →
    // success). Runs on every open — not just the first — since loadConversation
    // short-circuits once a conversation is cached. Gating on primitive id/status
    // keeps a redundant /seen POST (and loadConversation call) from firing while
    // the conversation is still "review" and unrelated state changes re-render.
    if (activeConversationStatus === "review") {
      markConversationSeen(activeConversationId);
    }
  }, [activeConversationId, activeConversationStatus]);

  useEffect(() => {
    const events = new EventSource("/api/events");

    events.addEventListener("connected", () => setStatusText(""));
    events.addEventListener("message-created", (event) => {
      const payload = JSON.parse(event.data);
      addMessageToConversation(payload.conversationId, payload.message);
    });
    events.addEventListener("message-deleted", (event) => {
      const payload = JSON.parse(event.data);
      removeMessageFromConversation(payload.conversationId, payload.message.id);
    });
    const refreshProjects = () => refreshProjectsFromServer();
    events.addEventListener("projects-updated", refreshProjects);
    events.addEventListener("project-updated", refreshProjects);
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
      const { id } = JSON.parse(event.data);
      refreshProjectsFromServer();
      refreshConversationFromServer(id);
    });
    events.addEventListener("conversation-status", (event) => {
      const { id, status } = JSON.parse(event.data);
      setProjects((currentProjects) =>
        currentProjects.map((project) => ({
          ...project,
          conversations: project.conversations.map((conversation) =>
            conversation.id === id ? { ...conversation, status } : conversation
          )
        }))
      );
      // A settled reply (anything but "running") means no thinking indicator
      // should linger. SSE has no ordering guarantee, so a reply's terminal
      // message-created can arrive before its status turn — drop any stale status
      // turn here so `busy` reconciles to server truth and can't stick.
      if (status !== "running") {
        dropStatusTurn(id);
      }
    });
    events.addEventListener("settings-updated", () => {
      refreshSettingsFromServer();
    });
    events.addEventListener("knowledge-scan-progress", (event) => {
      const { id, item } = JSON.parse(event.data);
      if (id && item) {
        recordScanProgress(id, item);
      }
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
                  name: conversation.name,
                  status: conversation.status ?? "idle"
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
      // The send was rejected (e.g. a reply is already in flight from another
      // tab) — restore the draft so the typed message isn't lost, unless the
      // user has already started a real new one (whitespace doesn't count).
      setDraftsByConversationId((currentDrafts) =>
        currentDrafts[conversationId]?.trim()
          ? currentDrafts
          : { ...currentDrafts, [conversationId]: content }
      );
    }
  }

  // Claim the in-flight slot for a retry/regenerate. Returns false when a reply
  // is already pending (a POST hasn't yet surfaced its status turn) or running
  // (the "thinking" turn is up), so the caller bails before issuing a duplicate
  // request. The flag is cleared once the server's response lands
  // (addMessageToConversation) or the POST fails.
  function beginReplyAction(conversationId) {
    if (pendingReplyActionsRef.current.has(conversationId)) {
      return false;
    }
    const conversation = conversationsById[conversationId];
    if (conversation?.messages.some((message) => message.role === "status")) {
      return false;
    }
    pendingReplyActionsRef.current.add(conversationId);
    return true;
  }

  // Re-run the last reply in place. The failed turn is transient (never
  // persisted), so drop it locally, then ask the backend to regenerate the
  // reply for the existing last user message — no duplicate prompt is added.
  async function retryConversation(conversationId) {
    if (!conversationsById[conversationId] || !beginReplyAction(conversationId)) {
      return;
    }
    setConversationsById((current) => {
      const conversation = current[conversationId];
      if (!conversation) {
        return current;
      }
      return {
        ...current,
        [conversationId]: {
          ...conversation,
          messages: conversation.messages.filter((message) => message.role !== "error")
        }
      };
    });
    try {
      await retryConversationReply(conversationId);
    } catch (error) {
      setStatusText(error.message);
      pendingReplyActionsRef.current.delete(conversationId);
    }
  }

  // Regenerate the last (successful, persisted) reply. The backend keeps the
  // existing assistant turn visible while it streams a fresh reply, then drops
  // the old one — emitting message-deleted, which removes it here — only once
  // the replacement has landed. So the turn is swapped rather than duplicated,
  // and a failed regenerate leaves the original reply intact.
  async function regenerateConversation(conversationId) {
    if (!conversationsById[conversationId] || !beginReplyAction(conversationId)) {
      return;
    }
    try {
      await regenerateConversationReply(conversationId);
    } catch (error) {
      setStatusText(error.message);
      pendingReplyActionsRef.current.delete(conversationId);
    }
  }

  // Tell the backend a review conversation has been opened. Best-effort: a
  // failure just leaves it in "review", so it's not surfaced as an error.
  async function markConversationSeen(conversationId) {
    try {
      await markConversationSeenRequest(conversationId);
    } catch {
      // ignored — status simply stays "review"
    }
  }

  function openProviderSettings() {
    setSettingsSection("providers");
    setActivePage("settings");
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

  async function refreshProjectsFromServer() {
    try {
      setProjects(await getProjects());
    } catch (error) {
      setStatusText(error.message);
    }
  }

  async function refreshSettingsFromServer() {
    try {
      const loadedSettings = await getSettings();
      setSettings(loadedSettings);
      setKnowledgeFolders(loadedSettings.knowledgeFolders ?? []);
    } catch (error) {
      setStatusText(error.message);
    }
  }

  async function refreshConversationFromServer(conversationId) {
    if (!conversationId) {
      return;
    }

    try {
      const conversation = await getConversation(conversationId);
      setConversationsById((currentConversations) =>
        conversation.id in currentConversations
          ? {
              ...currentConversations,
              [conversation.id]: conversation
            }
          : currentConversations
      );
    } catch (error) {
      setStatusText(error.message);
    }
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
    clearScanProgress(folderId);
    try {
      const folder = await scanKnowledgeFolderRequest(folderId);
      setKnowledgeFolders((currentFolders) => upsertById(currentFolders, folder));
    } catch (error) {
      setStatusText(error.message);
      throw error;
    }
  }

  function applyJiraSettings(jira) {
    setSettings((currentSettings) => ({ ...currentSettings, jira }));
    return jira;
  }

  async function saveJiraConnection(connection) {
    try {
      return applyJiraSettings(await saveJiraConnectionRequest(connection));
    } catch (error) {
      setStatusText(error.message);
      throw error;
    }
  }

  async function clearJiraConnection() {
    try {
      return applyJiraSettings(await clearJiraConnectionRequest());
    } catch (error) {
      setStatusText(error.message);
      throw error;
    }
  }

  async function searchJiraProjects(query) {
    try {
      return await searchJiraProjectsRequest(query);
    } catch (error) {
      setStatusText(error.message);
      throw error;
    }
  }

  async function addJiraProject(key) {
    try {
      const project = await addJiraProjectRequest(key);
      setSettings((currentSettings) => ({
        ...currentSettings,
        jira: {
          ...(currentSettings.jira ?? emptySettings.jira),
          projects: upsertById(currentSettings.jira?.projects ?? [], project)
        }
      }));
      return project;
    } catch (error) {
      setStatusText(error.message);
      throw error;
    }
  }

  async function removeJiraProject(projectId) {
    try {
      await removeJiraProjectRequest(projectId);
      setSettings((currentSettings) => ({
        ...currentSettings,
        jira: {
          ...(currentSettings.jira ?? emptySettings.jira),
          projects: (currentSettings.jira?.projects ?? []).filter((project) => project.id !== projectId)
        }
      }));
    } catch (error) {
      setStatusText(error.message);
      throw error;
    }
  }

  async function scanJiraProject(projectId) {
    clearScanProgress(projectId);
    try {
      // Fire-and-forget: the scan runs in the background and its live status
      // (scanning → scanned) arrives via SSE. Applying the immediate response here
      // races with — and can clobber — that SSE status, hiding the scanning ticker.
      await scanJiraProjectRequest(projectId);
    } catch (error) {
      setStatusText(error.message);
      throw error;
    }
  }

  async function toggleJiraProjectPause(project) {
    try {
      const savedProject =
        project.status === "paused"
          ? await resumeJiraProjectRequest(project.id)
          : await pauseJiraProjectRequest(project.id);
      setSettings((currentSettings) => ({
        ...currentSettings,
        jira: {
          ...(currentSettings.jira ?? emptySettings.jira),
          projects: upsertById(currentSettings.jira?.projects ?? [], savedProject)
        }
      }));
      return savedProject;
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

  function applyConfluenceSettings(confluence) {
    setSettings((currentSettings) => ({ ...currentSettings, confluence }));
    return confluence;
  }

  async function saveConfluenceConnection(connection) {
    try {
      return applyConfluenceSettings(await saveConfluenceConnectionRequest(connection));
    } catch (error) {
      setStatusText(error.message);
      throw error;
    }
  }

  async function clearConfluenceConnection() {
    try {
      return applyConfluenceSettings(await clearConfluenceConnectionRequest());
    } catch (error) {
      setStatusText(error.message);
      throw error;
    }
  }

  async function searchConfluenceSpaces(query) {
    try {
      return await searchConfluenceSpacesRequest(query);
    } catch (error) {
      setStatusText(error.message);
      throw error;
    }
  }

  async function addConfluenceSpace(key) {
    try {
      const space = await addConfluenceSpaceRequest(key);
      setSettings((currentSettings) => ({
        ...currentSettings,
        confluence: {
          ...(currentSettings.confluence ?? emptySettings.confluence),
          spaces: upsertById(currentSettings.confluence?.spaces ?? [], space)
        }
      }));
      return space;
    } catch (error) {
      setStatusText(error.message);
      throw error;
    }
  }

  async function removeConfluenceSpace(spaceId) {
    try {
      await removeConfluenceSpaceRequest(spaceId);
      setSettings((currentSettings) => ({
        ...currentSettings,
        confluence: {
          ...(currentSettings.confluence ?? emptySettings.confluence),
          spaces: (currentSettings.confluence?.spaces ?? []).filter((space) => space.id !== spaceId)
        }
      }));
    } catch (error) {
      setStatusText(error.message);
      throw error;
    }
  }

  async function scanConfluenceSpace(spaceId) {
    clearScanProgress(spaceId);
    try {
      // Fire-and-forget: the scan runs in the background and its live status
      // (scanning → scanned) arrives via SSE. Applying the immediate response here
      // races with — and can clobber — that SSE status, hiding the scanning ticker.
      await scanConfluenceSpaceRequest(spaceId);
    } catch (error) {
      setStatusText(error.message);
      throw error;
    }
  }

  async function toggleConfluenceSpacePause(space) {
    try {
      const savedSpace =
        space.status === "paused"
          ? await resumeConfluenceSpaceRequest(space.id)
          : await pauseConfluenceSpaceRequest(space.id);
      setSettings((currentSettings) => ({
        ...currentSettings,
        confluence: {
          ...(currentSettings.confluence ?? emptySettings.confluence),
          spaces: upsertById(currentSettings.confluence?.spaces ?? [], savedSpace)
        }
      }));
      return savedSpace;
    } catch (error) {
      setStatusText(error.message);
      throw error;
    }
  }

  // Append a streamed scan item for a source, keeping a short rolling window of
  // distinct recent names for the ticker to cycle through. Consecutive repeats
  // are ignored so a slow single-file scan doesn't stutter.
  function recordScanProgress(id, item) {
    setScanProgressById((current) => {
      const previous = current[id] ?? [];
      if (previous[previous.length - 1] === item) {
        return current;
      }
      const next = [...previous, item].slice(-5);
      return { ...current, [id]: next };
    });
  }

  // Drop a source's buffered scan items so a fresh scan's ticker starts empty
  // instead of replaying ticket keys / file names from the previous scan.
  function clearScanProgress(id) {
    setScanProgressById((current) => {
      if (!(id in current)) {
        return current;
      }
      const next = { ...current };
      delete next[id];
      return next;
    });
  }

  // Drop any lingering "thinking" status turn from a loaded conversation. Used to
  // reconcile `busy` to server truth when a terminal conversation-status arrives,
  // since SSE delivery isn't ordered and a status turn can outlive its reply.
  function dropStatusTurn(conversationId) {
    setConversationsById((current) => {
      const conversation = current[conversationId];
      if (!conversation || !conversation.messages.some((message) => message.role === "status")) {
        return current;
      }
      return {
        ...current,
        [conversationId]: {
          ...conversation,
          messages: conversation.messages.filter((message) => message.role !== "status")
        }
      };
    });
  }

  function removeMessageFromConversation(conversationId, messageId) {
    setConversationsById((currentConversations) => {
      const conversation = currentConversations[conversationId];
      if (!conversation) {
        return currentConversations;
      }
      return {
        ...currentConversations,
        [conversationId]: {
          ...conversation,
          messages: conversation.messages.filter((message) => message.id !== messageId)
        }
      };
    });
  }

  function addMessageToConversation(conversationId, message) {
    // The action's first server-driven turn (the "thinking" status, or a terminal
    // error if the pipeline never started) has landed, so the !busy gate now
    // guards re-clicks — release the synchronous pending flag.
    pendingReplyActionsRef.current.delete(conversationId);
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
        onOpenSettings={() => {
          setSettingsSection(null);
          setActivePage("settings");
        }}
        settingsActive={activePage === "settings"}
      />

      {activePage === "settings" ? (
        <Settings
          initialSection={settingsSection}
          onClose={() => setActivePage("work")}
          settings={settings}
          onSave={updateSettings}
          knowledgeFolders={knowledgeFolders}
          scanProgressById={scanProgressById}
          onAddKnowledgeFolder={addKnowledgeFolder}
          onRemoveKnowledgeFolder={removeKnowledgeFolder}
          onScanKnowledgeFolder={scanKnowledgeFolder}
          onToggleKnowledgeFolderPause={toggleKnowledgeFolderPause}
          jiraConfig={settings.jira ?? emptySettings.jira}
          onSaveJiraConnection={saveJiraConnection}
          onClearJiraConnection={clearJiraConnection}
          onSearchJiraProjects={searchJiraProjects}
          onAddJiraProject={addJiraProject}
          onRemoveJiraProject={removeJiraProject}
          onScanJiraProject={scanJiraProject}
          onToggleJiraProjectPause={toggleJiraProjectPause}
          confluenceConfig={settings.confluence ?? emptySettings.confluence}
          onSaveConfluenceConnection={saveConfluenceConnection}
          onClearConfluenceConnection={clearConfluenceConnection}
          onSearchConfluenceSpaces={searchConfluenceSpaces}
          onAddConfluenceSpace={addConfluenceSpace}
          onRemoveConfluenceSpace={removeConfluenceSpace}
          onScanConfluenceSpace={scanConfluenceSpace}
          onToggleConfluenceSpacePause={toggleConfluenceSpacePause}
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
            echoMode={!settings.aiModel || settings.aiModel === "none"}
            onCreateProject={addProject}
            onDraftChange={updateDraft}
            onOpenProviders={openProviderSettings}
            onProjectSave={updateProject}
            onProjectDelete={deleteProject}
            onRetry={retryConversation}
            onRegenerate={regenerateConversation}
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
