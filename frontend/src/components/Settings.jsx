import { useEffect, useRef, useState } from "react";
import { Icon } from "./Icon.jsx";
import { useServerSync } from "../hooks/useServerSync.js";
import {
  getAnthropicModels,
  getAzureOpenAiDeployments,
  getBedrockModels,
  getBedrockRegions,
  getDeepSeekModels,
  getGeminiModels,
  getGroqModels,
  getMistralModels,
  getOllamaModels,
  getOpenAiModels
} from "../api.js";

// Static catalog: presentation metadata + how each provider maps onto the
// live settings object and its model loader. Order matches the overview grid.
const PROVIDERS = [
  {
    id: "anthropic",
    name: "Anthropic",
    region: "United States",
    kind: "api-key",
    settingsKey: "anthropic",
    aiModelValue: "anthropic",
    blurb: "Claude family models.",
    loadModels: getAnthropicModels
  },
  {
    id: "openai",
    name: "OpenAI",
    region: "United States",
    kind: "api-key",
    settingsKey: "openAi",
    aiModelValue: "openai",
    blurb: "GPT models via the OpenAI API.",
    loadModels: getOpenAiModels,
    modelProvider: "openai"
  },
  {
    id: "openai-sdk",
    name: "OpenAI (SDK)",
    region: "United States",
    kind: "api-key",
    settingsKey: "openAiSdk",
    aiModelValue: "openai-sdk",
    blurb: "OpenAI through the official SDK transport.",
    loadModels: getOpenAiModels,
    modelProvider: "openai-sdk"
  },
  {
    id: "azure",
    name: "Azure OpenAI",
    region: "United States",
    kind: "endpoint",
    settingsKey: "azureOpenAi",
    aiModelValue: "azure-openai",
    blurb: "OpenAI models hosted on your Azure resource."
  },
  {
    id: "bedrock",
    name: "Bedrock",
    region: "United States",
    kind: "bedrock",
    settingsKey: "bedrock",
    aiModelValue: "bedrock",
    blurb: "Foundation models via the Bedrock Converse API.",
    loadModels: loadBedrockModels
  },
  {
    id: "mistral",
    name: "Mistral",
    region: "European Union · France",
    kind: "api-key",
    settingsKey: "mistral",
    aiModelValue: "mistral",
    blurb: "European open-weight and frontier models.",
    loadModels: getMistralModels
  },
  {
    id: "gemini",
    name: "Gemini",
    region: "United States",
    kind: "api-key",
    settingsKey: "gemini",
    aiModelValue: "gemini",
    blurb: "Google Gemini models.",
    loadModels: getGeminiModels
  },
  {
    id: "groq",
    name: "Groq",
    region: "United States",
    kind: "api-key",
    settingsKey: "groq",
    aiModelValue: "groq",
    blurb: "Fast inference on LPU hardware.",
    loadModels: getGroqModels
  },
  {
    id: "deepseek",
    name: "DeepSeek",
    region: "China",
    kind: "api-key",
    settingsKey: "deepSeek",
    aiModelValue: "deepseek",
    blurb: "DeepSeek reasoning and chat models.",
    loadModels: getDeepSeekModels
  },
  {
    id: "ollama",
    name: "Ollama",
    region: "On this device",
    kind: "local",
    settingsKey: "ollama",
    aiModelValue: "ollama",
    blurb: "Local models, fully offline. Nothing leaves your machine."
  }
];

const NAV = [
  { id: "general", label: "General", icon: "sliders" },
  { id: "providers", label: "Models & providers", icon: "cpu" },
  { id: "knowledge", label: "Knowledge", icon: "database" },
  { id: "tools", label: "Tools", icon: "wrench" }
];

// Static catalog of tools the agent can call. `enabledKey` maps each tool onto
// the boolean it toggles in the live settings object.
const TOOLS = [
  {
    id: "filesystem",
    name: "Filesystem",
    icon: "hard-drive",
    enabledKey: "filesystemToolEnabled",
    summary: "Read, write, and edit files in a project’s working folder.",
    description:
      "Exposes the current project’s working folder to the agent as the virtual root “/” — allowing safe file reads, writes, line-based edits, directory listing and tree views, file search, metadata inspection, media reads, and moves. Local absolute paths, traversal outside the folder, and unsafe symlinks are always rejected."
  }
];

function toolEnabled(tool, settings) {
  return settings?.[tool.enabledKey] !== false;
}

// Maximum number of continuously-scanned local folders / Jira projects.
const KNOWLEDGE_MAX = 10;
const JIRA_MAX = 10;

const DEFAULT_AWS_REGION = "us-east-1";

// Per-provider API-key prefix hints. Providers whose keys don't follow the
// "sk-" convention (Gemini, Groq, Mistral) get their own hint rather than a
// misleading "sk-…" placeholder.
const KEY_HINTS = {
  anthropic: "sk-ant-…",
  openai: "sk-…",
  "openai-sdk": "sk-…",
  deepseek: "sk-…",
  gemini: "AIza…",
  groq: "gsk_…"
};

// Human-readable labels for the Bedrock region IDs the backend returns, so the
// select reads "us-east-1 — US East (N. Virginia)" rather than a bare ID.
const AWS_REGION_LABELS = {
  "us-east-1": "US East (N. Virginia)",
  "us-east-2": "US East (Ohio)",
  "us-west-2": "US West (Oregon)",
  "ca-central-1": "Canada (Central)",
  "eu-central-1": "Europe (Frankfurt)",
  "eu-west-1": "Europe (Ireland)",
  "eu-west-3": "Europe (Paris)",
  "ap-northeast-1": "Asia Pacific (Tokyo)",
  "ap-southeast-1": "Asia Pacific (Singapore)",
  "ap-southeast-2": "Asia Pacific (Sydney)"
};

// Stable module-level loaders. ProviderModelSelect keeps `loadModels` in its
// effect deps, so these must NOT be inline closures or the effect re-runs every
// render and polls the model endpoint in a loop. Varying values (Ollama base
// URL, Azure endpoint) are threaded through the `lookupValue`/`provider` props.
function loadOllamaModels({ lookupValue }) {
  return getOllamaModels({ baseUrl: lookupValue });
}

function loadAzureDeployments({ lookupValue, provider, useSavedKey }) {
  return getAzureOpenAiDeployments({
    apiKey: lookupValue,
    endpoint: provider,
    useSavedKey
  });
}

function loadBedrockModels({
  lookupValue,
  provider,
  secretValue,
  useSavedKey,
  useSavedSecretKey
}) {
  return getBedrockModels({
    region: provider,
    accessKey: lookupValue,
    secretKey: secretValue,
    useSavedAccessKey: useSavedKey,
    useSavedSecretKey
  });
}

function providerState(provider, settings) {
  const config = settings?.[provider.settingsKey] ?? {};
  if (provider.kind === "local") {
    return { connected: Boolean(config.model), model: config.model ?? "" };
  }
  if (provider.kind === "endpoint") {
    return {
      connected: Boolean(config.apiKeyConfigured) && Boolean(config.endpoint),
      model: config.deploymentName ?? ""
    };
  }
  if (provider.kind === "bedrock") {
    return {
      connected:
        Boolean(config.region) &&
        Boolean(config.accessKeyConfigured) &&
        Boolean(config.secretKeyConfigured),
      model: config.model ?? ""
    };
  }
  return {
    connected: Boolean(config.apiKeyConfigured),
    model: config.model ?? ""
  };
}

export function Settings({
  initialSection,
  onClose,
  settings,
  onSave,
  knowledgeFolders,
  scanProgressById,
  onAddKnowledgeFolder,
  onRemoveKnowledgeFolder,
  onScanKnowledgeFolder,
  onToggleKnowledgeFolderPause,
  jiraConfig,
  onSaveJiraConnection,
  onClearJiraConnection,
  onSearchJiraProjects,
  onAddJiraProject,
  onRemoveJiraProject,
  onScanJiraProject,
  onToggleJiraProjectPause
}) {
  const [draft, setDraft] = useState(settings);
  const [section, setSection] = useState(initialSection || "general");
  const [openProviderId, setOpenProviderId] = useState(null);
  const [knowledgeView, setKnowledgeView] = useState(null); // null | "local-folders" | "jira"
  const [openToolId, setOpenToolId] = useState(null); // null | toolId

  useServerSync(settings, setDraft);

  const openProvider = PROVIDERS.find((p) => p.id === openProviderId) ?? null;
  const openTool = TOOLS.find((t) => t.id === openToolId) ?? null;

  function selectSection(id) {
    setSection(id);
    setOpenProviderId(null);
    setKnowledgeView(null);
    setOpenToolId(null);
  }

  // Tool toggles have no Save button (like the design's drill-in), so persist
  // immediately rather than waiting on the General/provider Save button.
  function toggleTool(tool, enabled) {
    const next = { ...draft, [tool.enabledKey]: enabled };
    setDraft(next);
    onSave(next);
  }

  function updateProviderConfig(settingsKey, patch) {
    setDraft((current) => ({
      ...current,
      [settingsKey]: { ...(current[settingsKey] ?? {}), ...patch }
    }));
  }

  return (
    <div className="settings">
      <header className="settings-topbar">
        <span className="topbar-title">
          <Icon name="settings" size={18} color="var(--gray-700)" />
          Settings
        </span>
        <button className="btn btn-secondary btn-sm" type="button" onClick={onClose}>
          <Icon name="arrow-left" size={14} color="var(--gray-700)" /> Back
        </button>
      </header>

      <div className="settings-cols">
        <nav className="settings-nav" aria-label="Settings sections">
          {NAV.map((item) => {
            const active = section === item.id;
            return (
              <button
                key={item.id}
                className={active ? "settings-nav-item active" : "settings-nav-item"}
                type="button"
                aria-current={active ? "page" : undefined}
                onClick={() => selectSection(item.id)}
              >
                <Icon
                  name={item.icon}
                  size={17}
                  color={active ? "var(--blue-600)" : "var(--gray-500)"}
                />
                {item.label}
              </button>
            );
          })}
        </nav>

        <div className="settings-body">
          {section === "general" && (
            <GeneralSettings
              draft={draft}
              setDraft={setDraft}
              onSave={() => onSave(draft)}
            />
          )}
          {section === "providers" &&
            (openProvider ? (
              <ProviderConfig
                key={openProvider.id}
                provider={openProvider}
                draft={draft}
                updateProviderConfig={updateProviderConfig}
                isDefault={draft.aiModel === openProvider.aiModelValue}
                onBack={() => setOpenProviderId(null)}
                onSave={() => onSave(draft)}
                onMakeDefault={() => {
                  const next = { ...draft, aiModel: openProvider.aiModelValue };
                  setDraft(next);
                  onSave(next);
                }}
              />
            ) : (
              <ProvidersOverview
                settings={draft}
                onOpen={setOpenProviderId}
              />
            ))}
          {section === "knowledge" &&
            (knowledgeView === "local-folders" ? (
              <LocalFoldersConfig
                folders={knowledgeFolders}
                scanProgressById={scanProgressById}
                onAddFolder={onAddKnowledgeFolder}
                onRemoveFolder={onRemoveKnowledgeFolder}
                onScanFolder={onScanKnowledgeFolder}
                onTogglePause={onToggleKnowledgeFolderPause}
                onBack={() => setKnowledgeView(null)}
              />
            ) : knowledgeView === "jira" ? (
              <JiraConfig
                config={jiraConfig}
                scanProgressById={scanProgressById}
                onSaveConnection={onSaveJiraConnection}
                onClearConnection={onClearJiraConnection}
                onSearchProjects={onSearchJiraProjects}
                onAddProject={onAddJiraProject}
                onRemoveProject={onRemoveJiraProject}
                onScanProject={onScanJiraProject}
                onToggleProjectPause={onToggleJiraProjectPause}
                onBack={() => setKnowledgeView(null)}
              />
            ) : (
              <KnowledgeOverview
                folders={knowledgeFolders}
                jira={jiraConfig}
                onOpenFolders={() => setKnowledgeView("local-folders")}
                onOpenJira={() => setKnowledgeView("jira")}
              />
            ))}
          {section === "tools" &&
            (openTool ? (
              <ToolConfig
                key={openTool.id}
                tool={openTool}
                enabled={toolEnabled(openTool, draft)}
                onBack={() => setOpenToolId(null)}
                onToggle={(enabled) => toggleTool(openTool, enabled)}
              />
            ) : (
              <ToolsOverview
                settings={draft}
                onOpen={setOpenToolId}
              />
            ))}
        </div>
      </div>
    </div>
  );
}

// Path display that keeps the leaf folder visible, ellipsizing the head when
// space is tight. Plain CSS ellipsis truncates the tail — exactly the leaf that
// distinguishes two similar paths — so we pin the leaf and clip the head.
// Handles both POSIX ("/") and Windows ("\\") separators.
function MiddlePath({ path }) {
  const cut = Math.max(path.lastIndexOf("/"), path.lastIndexOf("\\"));
  if (cut < 0) {
    return path;
  }
  return (
    <span className="middle-path">
      <span className="middle-path-head">{path.slice(0, cut + 1)}</span>
      <span className="middle-path-tail">{path.slice(cut + 1)}</span>
    </span>
  );
}

// Scan-status pill shared by Local Folders and Jira projects.
function ScanStatus({ status }) {
  if (status === "scanning") {
    return (
      <span className="badge badge-info">
        <Icon
          name="refresh-cw"
          size={12}
          color="var(--blue-700)"
          className="cda-spin"
        />
        Scanning
      </span>
    );
  }
  if (status === "paused") {
    return <span className="badge badge-neutral">Paused</span>;
  }
  if (status === "error") {
    return (
      <span className="badge badge-danger">
        <Icon name="alert-triangle" size={12} color="var(--danger-600)" />
        Error
      </span>
    );
  }
  return (
    <span className="badge badge-success">
      <span className="dot" />
      Scanned
    </span>
  );
}

function JiraProjectStatus({ status }) {
  if (status === "scanning") {
    return (
      <span className="badge badge-info">
        <Icon
          name="refresh-cw"
          size={12}
          color="var(--blue-700)"
          className="cda-spin"
        />
        Scanning
      </span>
    );
  }
  if (status === "paused") {
    return <span className="badge badge-neutral">Paused</span>;
  }
  if (status === "error") {
    return (
      <span className="badge badge-danger">
        <Icon name="alert-triangle" size={12} color="var(--danger-600)" />
        Error
      </span>
    );
  }
  return (
    <span className="badge badge-success">
      <span className="dot" />
      Scanned
    </span>
  );
}

// Inline validation/connection error, shared across folders + Jira so the same
// screen never teaches the user two different error treatments.
function InlineError({ children }) {
  if (!children) {
    return null;
  }
  return (
    <div className="inline-error">
      <Icon name="alert-triangle" size={14} color="var(--danger-600)" />
      <span>{children}</span>
    </div>
  );
}

// Cycles through source-specific scan items. While a scan runs, the backend
// streams the current file name / Jira ticket key over SSE; those land here as
// `items`, falling back to a static label before the first progress event.
function ScanningTicker({ items }) {
  const [index, setIndex] = useState(0);
  useEffect(() => {
    if (!items.length) {
      return undefined;
    }
    const timer = setInterval(
      () => setIndex((current) => (current + 1) % items.length),
      850
    );
    return () => clearInterval(timer);
  }, [items.length]);
  return (
    <span className="scan-ticker">
      <Icon name="refresh-cw" size={12} color="var(--blue-600)" className="cda-spin" />
      <span className="scan-ticker-label">Scanning</span>
      <span className="scan-ticker-item">{items[index] || "…"}</span>
    </span>
  );
}

// Compact roll-up strip shown under a knowledge source header.
function SourceStats({ items }) {
  return (
    <div className="source-stats">
      {items.map((stat) => (
        <div className="source-stat" key={stat.label}>
          <div
            className={
              stat.tone === "danger"
                ? "source-stat-value danger"
                : "source-stat-value"
            }
          >
            {stat.value}
          </div>
          <div className="ds-overline">{stat.label}</div>
        </div>
      ))}
    </div>
  );
}

// A capped, continuously-scanned list of sub-scopes. Local Folders and Jira
// projects are the same archetype — a limited list of scopes, each with a status
// pill and scan-now / pause / remove controls under a header add-row. Only the
// leading visual, titles, meta strings, ticker items and the add control differ;
// everything else lives here so the two surfaces can't drift apart.
function KnowledgeSourceList({
  items,
  max,
  noun,
  emptyText,
  confirmLabel,
  removeLabel,
  addControl,
  addBelow,
  addRowRef,
  renderLeading,
  renderTitle,
  renderStatus,
  tickerItems,
  metaScanned,
  metaPaused,
  metaError,
  scanActionLabel = "Scan now",
  pauseActionLabel = "Pause scanning",
  resumeActionLabel = "Resume scanning",
  onScanNow,
  onTogglePause,
  onRemove
}) {
  const [confirmId, setConfirmId] = useState(null);
  return (
    <div className="ds-card folders-card">
      <div className="folder-add" ref={addRowRef}>
        <div className="folder-add-row">
          <span className="folder-count">
            {items.length}{" "}
            <span className="folder-count-max">/ {max} {noun}</span>
          </span>
          {addControl}
        </div>
        {addBelow}
      </div>

      {items.length === 0 ? (
        <div className="folder-empty">{emptyText}</div>
      ) : (
        items.map((item) => (
          <div className="folder-row" key={item.id}>
            {renderLeading(item)}
            <div className="folder-row-id">
              <div className="folder-path">{renderTitle(item)}</div>
              <div
                className={
                  item.status === "error" ? "folder-meta error" : "folder-meta"
                }
              >
                {item.status === "scanning" ? (
                  <ScanningTicker items={tickerItems(item)} />
                ) : item.status === "paused" ? (
                  metaPaused(item)
                ) : item.status === "error" ? (
                  metaError
                    ? metaError(item)
                    : "Source unavailable — last scan failed."
                ) : (
                  metaScanned(item)
                )}
              </div>
            </div>
            {confirmId === item.id ? (
              <div className="folder-confirm">
                <span className="folder-confirm-prompt">{confirmLabel}</span>
                <button
                  className="btn btn-secondary btn-sm"
                  type="button"
                  onClick={() => setConfirmId(null)}
                >
                  Cancel
                </button>
                <button
                  className="btn btn-danger btn-sm"
                  type="button"
                  onClick={() => {
                    onRemove(item.id);
                    setConfirmId(null);
                  }}
                >
                  Remove
                </button>
              </div>
            ) : (
              <div className="folder-row-controls">
                {renderStatus ? renderStatus(item) : <ScanStatus status={item.status} />}
                <button
                  className="iconbtn"
                  type="button"
                  title={item.status === "error" ? "Retry scan" : scanActionLabel}
                  aria-label={item.status === "error" ? "Retry scan" : scanActionLabel}
                  onClick={() => onScanNow(item.id)}
                  disabled={item.status === "paused" || item.status === "scanning"}
                >
                  <Icon
                    name="refresh-cw"
                    size={16}
                    color={
                      item.status === "error"
                        ? "var(--danger-600)"
                        : "var(--gray-500)"
                    }
                  />
                </button>
                {item.status === "error" ? (
                  <button
                    className="iconbtn"
                    type="button"
                    title="Pause unavailable while the source can't be reached"
                    aria-label={pauseActionLabel}
                    disabled
                  >
                    <Icon name="pause" size={16} color="var(--gray-500)" />
                  </button>
                ) : (
                  <button
                    className="iconbtn"
                    type="button"
                    title={item.status === "paused" ? resumeActionLabel : pauseActionLabel}
                    aria-label={item.status === "paused" ? resumeActionLabel : pauseActionLabel}
                    onClick={() => onTogglePause(item.id)}
                  >
                    <Icon
                      name={item.status === "paused" ? "play" : "pause"}
                      size={16}
                      color="var(--gray-500)"
                    />
                  </button>
                )}
                <button
                  className="iconbtn"
                  type="button"
                  title={removeLabel}
                  aria-label={removeLabel}
                  onClick={() => setConfirmId(item.id)}
                >
                  <Icon name="trash" size={16} color="var(--gray-500)" />
                </button>
              </div>
            )}
          </div>
        ))
      )}
    </div>
  );
}

function KnowledgeOverview({ folders, jira, onOpenFolders, onOpenJira }) {
  const scanning = folders.filter((f) => f.status === "scanning").length;
  const folderSummary = scanning
    ? `${scanning} scanning now`
    : folders.length
      ? "Auto-scanning · up to date"
      : "No folders yet";

  const jiraProjects = jira?.projects ?? [];
  const jiraScanning = jiraProjects.filter((p) => p.status === "scanning").length;
  const jiraErrors = jiraProjects.filter((p) => p.status === "error").length;
  const jiraSummary = !jira?.connected
    ? "Not connected"
    : jiraScanning
      ? `${jiraScanning} scanning now`
      : jiraErrors
        ? `${jiraErrors} need attention`
        : jiraProjects.length
          ? "Connected · ready to scan"
          : "No projects yet";

  return (
    <div className="settings-section wide">
      <div className="settings-intro">
        <h2 className="ds-h3">Knowledge</h2>
        <p className="ds-body">
          Sources the agent draws on to understand your work context. Local
          folders and Jira issues are indexed locally after you choose what to
          scan.
        </p>
      </div>
      <div className="providers-grid">
        {/* Local Folders tile */}
        <button className="provider-card" type="button" onClick={onOpenFolders}>
          <div className="provider-card-head">
            <span className="provider-icon local">
              <Icon name="folder-open" size={19} color="var(--coffee-700)" />
            </span>
            <div className="provider-id">
              <div className="provider-name">Local Folders</div>
              <div className="provider-region">
                {folders.length} of {KNOWLEDGE_MAX} folders · continuously scanned
              </div>
            </div>
          </div>
          <div className="knowledge-tile-badge">
            <span className="badge badge-info">
              <Icon name="shield-check" size={12} color="var(--blue-700)" />
              On device
            </span>
          </div>
          <div className="provider-card-foot">
            <span className="folder-summary">
              <Icon name="circle-dot" size={12} color="var(--gray-400)" />
              {folderSummary}
            </span>
            <span className="provider-configure">
              Manage
              <Icon name="chevron-right" size={13} color="var(--blue-600)" />
            </span>
          </div>
        </button>

        {/* Jira tile */}
        <button className="provider-card" type="button" onClick={onOpenJira}>
          <div className="provider-card-head">
            <span className="provider-icon">
              <Icon name="ticket" size={19} color="var(--blue-600)" />
            </span>
            <div className="provider-id">
              <div className="provider-name">Jira (API)</div>
              <div className="provider-region">
                {jiraProjects.length} of {JIRA_MAX} project slots · manual scans
              </div>
            </div>
          </div>
          <div className="knowledge-tile-badge">
            {jira?.connected ? (
              <span className="badge badge-success">
                <span className="dot" />
                Connected
              </span>
            ) : (
              <span className="badge badge-neutral">Not connected</span>
            )}
          </div>
          <div className="provider-card-foot">
            <span className="folder-summary">
              <Icon name="circle-dot" size={12} color="var(--gray-400)" />
              {jiraSummary}
            </span>
            <span className="provider-configure">
              {jira?.connected ? "Manage" : "Configure"}
              <Icon name="chevron-right" size={13} color="var(--blue-600)" />
            </span>
          </div>
        </button>
      </div>
    </div>
  );
}

function LocalFoldersConfig({
  folders,
  scanProgressById,
  onAddFolder,
  onRemoveFolder,
  onScanFolder,
  onTogglePause,
  onBack
}) {
  const [draft, setDraft] = useState("");
  const [error, setError] = useState("");
  const [checking, setChecking] = useState(false);

  const atMax = folders.length >= KNOWLEDGE_MAX;
  const totalFiles = folders.reduce((total, folder) => total + Number(folder.files ?? 0), 0);
  const scanningCount = folders.filter((folder) => folder.status === "scanning").length;
  const pausedCount = folders.filter((folder) => folder.status === "paused").length;
  const errorCount = folders.filter((folder) => folder.status === "error").length;

  async function addFolder() {
    const path = draft.trim();
    if (checking || atMax || !path) {
      return;
    }
    setError("");
    setChecking(true);
    try {
      await onAddFolder(path);
      setDraft("");
    } catch (addError) {
      setError(addError.message || "Could not add that folder.");
    } finally {
      setChecking(false);
    }
  }

  async function removeFolder(id) {
    try {
      await onRemoveFolder(id);
    } catch (removeError) {
      setError(removeError.message || "Could not remove that folder.");
    }
  }

  async function scanNow(id) {
    try {
      await onScanFolder(id);
    } catch (scanError) {
      setError(scanError.message || "Could not scan that folder.");
    }
  }

  async function togglePause(id) {
    const folder = folders.find((item) => item.id === id);
    if (!folder) {
      return;
    }
    try {
      await onTogglePause(folder);
    } catch (pauseError) {
      setError(pauseError.message || "Could not update that folder.");
    }
  }

  function folderMeta(folder) {
    const files = Number(folder.files ?? 0).toLocaleString();
    const size = folder.size || "not scanned yet";
    const checked = folder.checked ? ` · checked ${folder.checked}` : "";
    return `${files} files · ${size}${checked}`;
  }

  return (
    <div className="settings-section">
      <button className="config-back" type="button" onClick={onBack}>
        <Icon name="arrow-left" size={15} color="var(--gray-600)" /> All knowledge
      </button>

      <div className="config-head">
        <span className="provider-icon local lg">
          <Icon name="folder-open" size={22} color="var(--coffee-700)" />
        </span>
        <div className="provider-id">
          <h2 className="ds-h3">Local Folders</h2>
          <div className="provider-region">
            Folders on this device — including the local copies of synced OneDrive
            and SharePoint libraries. The agent re-scans them automatically to keep
            its context current; files never leave this device. Up to {KNOWLEDGE_MAX}.
          </div>
        </div>
        <span className="badge badge-info knowledge-head-badge">
          <Icon name="shield-check" size={12} color="var(--blue-700)" />
          On device
        </span>
      </div>

      {folders.length > 0 && (
        <SourceStats
          items={[
            { label: "Files indexed", value: totalFiles.toLocaleString() },
            { label: "Folders", value: `${folders.length} / ${KNOWLEDGE_MAX}` },
            {
              label: "Status",
              value: scanningCount
                ? `${scanningCount} scanning`
                : pausedCount
                  ? `${pausedCount} paused`
                  : errorCount
                    ? `${errorCount} error${errorCount === 1 ? "" : "s"}`
                    : "Up to date",
              tone:
                !scanningCount && !pausedCount && errorCount ? "danger" : undefined
            }
          ]}
        />
      )}

      <KnowledgeSourceList
        items={folders}
        max={KNOWLEDGE_MAX}
        noun="folders"
        emptyText="No folders yet. Add one above to start building local context."
        confirmLabel="Remove folder?"
        removeLabel="Remove folder"
        onScanNow={scanNow}
        onTogglePause={togglePause}
        onRemove={removeFolder}
        renderLeading={(folder) => (
          <Icon
            name="folder"
            size={18}
            color={folder.status === "error" ? "var(--danger-600)" : "var(--gray-500)"}
          />
        )}
        renderTitle={(folder) => <MiddlePath path={folder.path} />}
        tickerItems={(folder) => scanProgressById?.[folder.id] ?? []}
        metaScanned={folderMeta}
        metaPaused={(folder) => `Paused · ${folderMeta(folder)}`}
        metaError={(folder) =>
          folder.message ||
          "Folder not found — it may have been moved, renamed, or unmounted."
        }
        addControl={
          <div className="folder-add-controls">
            <span className="input-icon">
              <Icon name="folder" size={16} />
              <input
                className={error ? "input has-error" : "input"}
                type="text"
                placeholder={atMax ? "Folder limit reached" : "Add a folder path…"}
                value={draft}
                disabled={atMax}
                onChange={(event) => {
                  setDraft(event.target.value);
                  if (error) {
                    setError("");
                  }
                }}
                onKeyDown={(event) => {
                  if (event.key === "Enter") {
                    addFolder();
                  }
                }}
              />
            </span>
            <button
              className="btn btn-primary btn-sm"
              type="button"
              onClick={addFolder}
              disabled={atMax || !draft.trim() || checking}
            >
              {checking ? (
                <>
                  <Icon name="refresh-cw" size={15} color="#fff" className="cda-spin" />
                  Checking…
                </>
              ) : (
                <>
                  <Icon name="plus" size={15} color="#fff" /> Add
                </>
              )}
            </button>
          </div>
        }
        addBelow={error ? <InlineError>{error}</InlineError> : null}
      />

      <div className="knowledge-privacy">
        <Icon name="shield-check" size={14} color="var(--success-600)" />
        Files are indexed locally on this device.
      </div>
    </div>
  );
}

function JiraConfig({
  config,
  scanProgressById,
  onSaveConnection,
  onClearConnection,
  onSearchProjects,
  onAddProject,
  onRemoveProject,
  onScanProject,
  onToggleProjectPause,
  onBack
}) {
  const [cfg, setCfg] = useState(() => ({
    instanceUrl: config.instanceUrl ?? "",
    email: config.email ?? "",
    connected: Boolean(config.connected),
    apiVersion: config.apiVersion ?? "3",
    tokenConfigured: Boolean(config.tokenConfigured),
    tokenLastFour: config.tokenLastFour ?? "",
    tokenExpiresDays: config.tokenExpiresDays ?? null,
    projects: config.projects ?? []
  }));
  const [instanceUrl, setInstanceUrl] = useState(config.instanceUrl ?? "");
  const [email, setEmail] = useState(config.email ?? "");
  const [token, setToken] = useState("");
  const [pendingClear, setPendingClear] = useState(false);
  const [connecting, setConnecting] = useState(false);
  const [connectError, setConnectError] = useState("");
  const [disconnectConfirm, setDisconnectConfirm] = useState(false);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [pickerSearch, setPickerSearch] = useState("");
  const [available, setAvailable] = useState([]);
  const [pickerLoading, setPickerLoading] = useState(false);
  const [addingProjectKey, setAddingProjectKey] = useState("");
  const [scanningProjectIds, setScanningProjectIds] = useState(() => new Set());
  const pickerRef = useRef(null);

  useServerSync(config, (next) => {
    const nextCfg = {
      instanceUrl: next.instanceUrl ?? "",
      email: next.email ?? "",
      connected: Boolean(next.connected),
      apiVersion: next.apiVersion ?? "3",
      tokenConfigured: Boolean(next.tokenConfigured),
      tokenLastFour: next.tokenLastFour ?? "",
      tokenExpiresDays: next.tokenExpiresDays ?? null,
      projects: next.projects ?? []
    };
    setCfg(nextCfg);
    setInstanceUrl(nextCfg.instanceUrl);
    setEmail(nextCfg.email);
  });

  const hasSaved = cfg.tokenConfigured;
  const projects = cfg.projects;
  const visibleProjects = projects.map((project) =>
    scanningProjectIds.has(project.id) ? { ...project, status: "scanning" } : project
  );
  const atMax = projects.length >= JIRA_MAX;
  const totalIssues = projects.reduce((total, project) => total + Number(project.issues ?? 0), 0);
  const scanningCount = visibleProjects.filter((project) => project.status === "scanning").length;
  const pausedCount = visibleProjects.filter((project) => project.status === "paused").length;
  const errorCount = visibleProjects.filter((project) => project.status === "error").length;
  // Days until the saved API token expires; under 14 we nudge the user to renew.
  const expiry = cfg.tokenExpiresDays;
  const expirySoon = typeof expiry === "number" && expiry <= 14;

  // Close the project picker on an outside click or the Escape key.
  useEffect(() => {
    if (!pickerOpen) {
      return undefined;
    }
    const close = () => {
      setPickerOpen(false);
      setPickerSearch("");
    };
    const onDown = (event) => {
      if (pickerRef.current && !pickerRef.current.contains(event.target)) {
        close();
      }
    };
    const onKey = (event) => {
      if (event.key === "Escape") {
        close();
      }
    };
    document.addEventListener("mousedown", onDown);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDown);
      document.removeEventListener("keydown", onKey);
    };
  }, [pickerOpen]);

  useEffect(() => {
    if (!pickerOpen || atMax) {
      setAvailable([]);
      return undefined;
    }
    let cancelled = false;
    setPickerLoading(true);
    onSearchProjects(pickerSearch)
      .then((projects) => {
        if (!cancelled) {
          setAvailable(projects);
        }
      })
      .catch((error) => {
        if (!cancelled) {
          setConnectError(error.message || "Could not load Jira projects.");
          setAvailable([]);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setPickerLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [pickerOpen, pickerSearch, atMax, projects.length]);

  function saveConnection() {
    const url = instanceUrl.trim();
    const mail = email.trim();
    if (!url) {
      setConnectError("Enter your Jira instance URL.");
      return;
    }
    if (!/^https?:\/\//.test(url)) {
      setConnectError("Instance URL must start with https://");
      return;
    }
    if (!mail) {
      setConnectError("Enter your email address.");
      return;
    }
    if (!mail.includes("@")) {
      setConnectError("Enter a valid email address.");
      return;
    }
    const trimmedToken = token.trim();
    if (!trimmedToken && !hasSaved && !pendingClear) {
      setConnectError("Enter an API token.");
      return;
    }
    setConnecting(true);
    setConnectError("");
    onSaveConnection({
      instanceUrl: url,
      email: mail,
      token: trimmedToken,
      clearToken: pendingClear
    })
      .then((savedCfg) => {
        setCfg(savedCfg);
        setToken("");
        setPendingClear(false);
      })
      .catch((error) => {
        setConnectError(error.message || "Could not save Jira setup.");
      })
      .finally(() => {
        setConnecting(false);
      });
  }

  function disconnect() {
    onClearConnection()
      .then((savedCfg) => {
        setCfg(savedCfg);
        setDisconnectConfirm(false);
        setToken("");
        setPendingClear(false);
      })
      .catch((error) => {
        setConnectError(error.message || "Could not clear Jira setup.");
      });
  }

  async function addProjectByKey(key) {
    if (addingProjectKey || atMax || projects.some((project) => project.key === key)) {
      return;
    }
    setAddingProjectKey(key);
    try {
      const project = await onAddProject(key);
      setCfg((prev) => ({
        ...prev,
        projects: prev.projects.some((item) => item.id === project.id)
          ? prev.projects.map((item) => (item.id === project.id ? project : item))
          : [...prev.projects, project]
      }));
      setPickerSearch("");
      setScanningProjectIds((current) => new Set(current).add(project.id));
      try {
        const scannedProject = await onScanProject(project.id);
        setCfg((current) => ({
          ...current,
          projects: current.projects.map((item) =>
            item.id === scannedProject.id ? scannedProject : item
          )
        }));
      } catch (error) {
        setConnectError(error.message || "Project added, but Jira scan failed.");
      } finally {
        setScanningProjectIds((current) => {
          const next = new Set(current);
          next.delete(project.id);
          return next;
        });
      }
    } catch (error) {
      setConnectError(error.message || "Could not add Jira project.");
    } finally {
      setAddingProjectKey("");
    }
  }

  async function removeProject(id) {
    try {
      await onRemoveProject(id);
      setCfg((current) => ({
        ...current,
        projects: current.projects.filter((project) => project.id !== id)
      }));
    } catch (error) {
      setConnectError(error.message || "Could not remove Jira project.");
    }
  }

  async function togglePause(id) {
    const project = projects.find((item) => item.id === id);
    if (!project) {
      return;
    }
    try {
      const savedProject = await onToggleProjectPause(project);
      setCfg((current) => ({
        ...current,
        projects: current.projects.map((item) =>
          item.id === savedProject.id ? savedProject : item
        )
      }));
    } catch (error) {
      setConnectError(error.message || "Could not update Jira project.");
    }
  }

  async function scanNow(id) {
    if (scanningProjectIds.has(id)) {
      return;
    }
    setScanningProjectIds((current) => new Set(current).add(id));
    try {
      const savedProject = await onScanProject(id);
      setCfg((current) => ({
        ...current,
        projects: current.projects.map((project) =>
          project.id === savedProject.id ? savedProject : project
        )
      }));
    } catch (error) {
      setConnectError(error.message || "Could not scan Jira project.");
    } finally {
      setScanningProjectIds((current) => {
        const next = new Set(current);
        next.delete(id);
        return next;
      });
    }
  }

  return (
    <div className="settings-section">
      <button className="config-back" type="button" onClick={onBack}>
        <Icon name="arrow-left" size={15} color="var(--gray-600)" /> All knowledge
      </button>

      <div className="config-head">
        <span className="provider-icon lg">
          <Icon name="ticket" size={22} color="var(--blue-600)" />
        </span>
        <div className="provider-id">
          <h2 className="ds-h3">Jira (API)</h2>
          <div className="provider-region">
            Validate Jira credentials, choose projects, and manually scan issues
            into the local knowledge index. Up to {JIRA_MAX} projects.
          </div>
        </div>
        {cfg.connected ? (
          <span className="badge badge-success knowledge-head-badge">
            <span className="dot" />
            Connected
          </span>
        ) : (
          <span className="badge badge-neutral knowledge-head-badge">Not connected</span>
        )}
      </div>

      {cfg.connected && projects.length > 0 && (
        <SourceStats
          items={[
            { label: "Jira issues", value: totalIssues.toLocaleString() },
            { label: "Projects", value: `${projects.length} / ${JIRA_MAX}` },
            {
              label: "Status",
              value: scanningCount
                ? `${scanningCount} scanning`
                : pausedCount
                  ? `${pausedCount} paused`
                  : errorCount
                    ? `${errorCount} error${errorCount === 1 ? "" : "s"}`
                    : "Ready",
              tone:
                !scanningCount && !pausedCount && errorCount ? "danger" : undefined
            }
          ]}
        />
      )}

      <div className="ds-card" style={{ display: "flex", flexDirection: "column", gap: 18 }}>
        <Field label="Instance URL" hint="Your Atlassian Cloud or Server base URL.">
          <input
            className="input"
            type="text"
            placeholder="https://your-org.atlassian.net"
            value={instanceUrl}
            onChange={(event) => {
              setInstanceUrl(event.target.value);
              setConnectError("");
            }}
          />
        </Field>
        <Field label="Email" hint="The email address tied to your Jira account.">
          <input
            className="input"
            type="email"
            placeholder="you@company.com"
            value={email}
            onChange={(event) => {
              setEmail(event.target.value);
              setConnectError("");
            }}
          />
        </Field>
        <div className="field">
          <label className="field">
            <span className="field-label">API token</span>
            <span className="input-icon">
              <Icon name="key" size={16} />
              <input
                className="input"
                type="password"
                placeholder={hasSaved ? `Saved token · ending ${cfg.tokenLastFour}` : ""}
                value={token}
                onChange={(event) => {
                  setToken(event.target.value);
                  setConnectError("");
                  setPendingClear(false);
                }}
              />
            </span>
          </label>
          {hasSaved && !pendingClear ? (
            <span className="saved-key-row">
              <span className="saved-key-text">
                <Icon name="circle-check" size={13} color="var(--success-600)" />
                Saved token ending {cfg.tokenLastFour}
              </span>
              <button
                className="btn btn-ghost btn-sm"
                type="button"
                onClick={() => setPendingClear(true)}
              >
                Clear
              </button>
            </span>
          ) : hasSaved && pendingClear ? (
            <span className="saved-key-row">
              <span className="token-clear-warning">
                <Icon name="alert-triangle" size={13} color="var(--warning-700)" />
                Token and selected projects will be cleared on save.
              </span>
              <button
                className="btn btn-ghost btn-sm"
                type="button"
                onClick={() => setPendingClear(false)}
              >
                Undo
              </button>
            </span>
          ) : (
            <span className="field-hint">
              Generate one at id.atlassian.com/manage-profile/security/api-tokens.
              Stored encrypted on this device — never synced.
            </span>
          )}
          {hasSaved && !pendingClear && typeof expiry === "number" && (
            <span className={expirySoon ? "jira-expiry soon" : "jira-expiry"}>
              <Icon
                name={expirySoon ? "alert-triangle" : "calendar"}
                size={13}
                color={expirySoon ? "var(--warning-700)" : "var(--gray-400)"}
              />
              {expirySoon
                ? `Token expires in ${expiry} days — generate a new one before enabling live sync.`
                : `Token expires in ${expiry} days.`}
            </span>
          )}
        </div>
        {connectError && <InlineError>{connectError}</InlineError>}
      </div>

      <div className="btn-row">
        <button
          className="btn btn-primary"
          type="button"
          onClick={saveConnection}
          disabled={connecting}
        >
          {connecting ? (
            <>
              <Icon name="refresh-cw" size={15} color="#fff" className="cda-spin" />
              Saving…
            </>
          ) : cfg.connected ? (
            <>
              <Icon name="check" size={16} color="#fff" /> Save connection
            </>
          ) : (
            <>
              <Icon name="check" size={16} color="#fff" /> Connect
            </>
          )}
        </button>
        {cfg.connected && !disconnectConfirm && (
          <button
            className="btn btn-secondary"
            type="button"
            onClick={() => setDisconnectConfirm(true)}
          >
            Clear setup
          </button>
        )}
        {cfg.connected && disconnectConfirm && (
          <>
            <span className="disconnect-prompt">
              Remove all {projects.length} project{projects.length !== 1 ? "s" : ""} too?
            </span>
            <button
              className="btn btn-secondary btn-sm"
              type="button"
              onClick={() => setDisconnectConfirm(false)}
            >
              Cancel
            </button>
            <button className="btn btn-danger btn-sm" type="button" onClick={disconnect}>
              <Icon name="trash" size={14} color="#fff" /> Clear setup
            </button>
          </>
        )}
      </div>

      {cfg.connected && (
        <KnowledgeSourceList
          items={visibleProjects}
          max={JIRA_MAX}
          noun="projects"
          addRowRef={pickerRef}
          emptyText="No projects yet. Add a Jira project above to scan its issues."
          confirmLabel="Remove project?"
          removeLabel="Remove project"
          onScanNow={scanNow}
          onTogglePause={togglePause}
          onRemove={removeProject}
          renderLeading={(project) => (
            <span
              className={project.status === "error" ? "jira-key error" : "jira-key"}
            >
              {project.key}
            </span>
          )}
          renderTitle={(project) => project.name}
          renderStatus={(project) => <JiraProjectStatus status={project.status} />}
          tickerItems={(project) => scanProgressById?.[project.id] ?? []}
          metaScanned={(project) =>
            `${Number(project.issues ?? 0).toLocaleString()} indexed Jira issues - scanned ${project.checked || "just now"}`
          }
          metaPaused={(project) =>
            `Paused - ${Number(project.issues ?? 0).toLocaleString()} indexed Jira issues`
          }
          metaError={(project) =>
            project.message ||
            "Couldn’t reach this project — check your access and retry."
          }
          scanActionLabel="Scan now"
          pauseActionLabel="Pause saved project"
          resumeActionLabel="Resume saved project"
          addControl={
            pickerOpen ? (
              <button
                className="btn btn-secondary btn-sm"
                type="button"
                onClick={() => {
                  setPickerOpen(false);
                  setPickerSearch("");
                }}
              >
                <Icon name="check" size={15} color="var(--gray-700)" /> Done
              </button>
            ) : (
              <button
                className="btn btn-primary btn-sm"
                type="button"
                onClick={() => {
                  setPickerOpen(true);
                  setPickerSearch("");
                }}
                disabled={atMax}
              >
                <Icon name="plus" size={15} color="#fff" />{" "}
                {atMax ? "Project limit reached" : "Add project"}
              </button>
            )
          }
          addBelow={
            pickerOpen && !atMax ? (
              <div className="jira-picker">
                <span className="input-icon jira-picker-search">
                  <Icon name="search" size={16} />
                  <input
                    className="input"
                    type="text"
                    autoFocus
                    placeholder="Search Jira projects..."
                    value={pickerSearch}
                    onChange={(event) => setPickerSearch(event.target.value)}
                  />
                </span>
                <div className="jira-picker-list">
                  {pickerLoading ? (
                    <div className="jira-picker-empty">Loading projects...</div>
                  ) : available.length === 0 ? (
                    <div className="jira-picker-empty">
                      {pickerSearch ? "No matching projects." : "Every project is already added."}
                    </div>
                  ) : (
                    available.map((project) => (
                      <button
                        key={project.key}
                        type="button"
                        className="jira-picker-item"
                        disabled={Boolean(addingProjectKey)}
                        onClick={() => addProjectByKey(project.key)}
                      >
                        <span className="jira-key">{project.key}</span>
                        <span className="jira-picker-name">{project.name}</span>
                        <Icon
                          name={addingProjectKey === project.key ? "refresh-cw" : "plus"}
                          size={15}
                          color="var(--blue-600)"
                          className={addingProjectKey === project.key ? "cda-spin" : ""}
                        />
                      </button>
                    ))
                  )}
                </div>
              </div>
            ) : null
          }
        />
      )}

      <div className="knowledge-privacy">
        <Icon name="shield-check" size={14} color="var(--success-600)" />
        Jira issues are fetched, chunked, and indexed locally on this device.
      </div>
    </div>
  );
}

function ProvidersOverview({ settings, onOpen }) {
  return (
    <div className="settings-section wide">
      <div className="settings-intro">
        <h2 className="ds-h3">Models &amp; providers</h2>
        <p className="ds-body">
          Connect the model providers you want the agent to use. Keys are stored
          locally on this device.
        </p>
      </div>
      <div className="providers-grid">
        {PROVIDERS.map((provider) => {
          const state = providerState(provider, settings);
          const isDefault = settings.aiModel === provider.aiModelValue;
          const isLocal = provider.kind === "local";
          return (
            <button
              key={provider.id}
              className="provider-card"
              type="button"
              onClick={() => onOpen(provider.id)}
            >
              <div className="provider-card-head">
                <span className={isLocal ? "provider-icon local" : "provider-icon"}>
                  <Icon
                    name={isLocal ? "cpu" : "bot"}
                    size={19}
                    color={isLocal ? "var(--coffee-700)" : "var(--blue-600)"}
                  />
                </span>
                <div className="provider-id">
                  <div className="provider-name-row">
                    <span className="provider-name">{provider.name}</span>
                    {isDefault && (
                      <span className="badge badge-info" style={{ padding: "2px 7px" }}>
                        Default
                      </span>
                    )}
                  </div>
                  <div className="provider-region">
                    <Icon name="globe" size={12} color="var(--gray-400)" />
                    {provider.region}
                  </div>
                </div>
              </div>
              <div className="provider-card-foot">
                <ProviderStatus provider={provider} connected={state.connected} />
                {state.connected && state.model ? (
                  <span className="provider-model">{state.model}</span>
                ) : (
                  <span className="provider-configure">
                    Configure
                    <Icon name="chevron-right" size={13} color="var(--blue-600)" />
                  </span>
                )}
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
}

function ProviderStatus({ provider, connected }) {
  if (provider.kind === "local" && connected) {
    return (
      <span className="badge badge-info">
        <Icon name="shield-check" size={12} color="var(--blue-700)" />
        On device
      </span>
    );
  }
  return connected ? (
    <span className="badge badge-success">
      <span className="dot" />
      Connected
    </span>
  ) : (
    <span className="badge badge-neutral">Not connected</span>
  );
}

function ProviderConfig({
  provider,
  draft,
  updateProviderConfig,
  isDefault,
  onBack,
  onSave,
  onMakeDefault
}) {
  const config = draft[provider.settingsKey] ?? {};
  const state = providerState(provider, draft);
  const isLocal = provider.kind === "local";

  return (
    <div className="settings-section">
      <button className="config-back" type="button" onClick={onBack}>
        <Icon name="arrow-left" size={15} color="var(--gray-600)" /> All providers
      </button>

      <div className="config-head">
        <span className={isLocal ? "provider-icon local lg" : "provider-icon lg"}>
          <Icon
            name={isLocal ? "cpu" : "bot"}
            size={22}
            color={isLocal ? "var(--coffee-700)" : "var(--blue-600)"}
          />
        </span>
        <div className="provider-id">
          <h2 className="ds-h3">{provider.name}</h2>
          <div className="provider-region">
            <Icon name="globe" size={12} color="var(--gray-400)" />
            {provider.region} · {provider.blurb}
          </div>
        </div>
        <ProviderStatus provider={provider} connected={state.connected} />
      </div>

      <div className="ds-card" style={{ display: "flex", flexDirection: "column", gap: 18 }}>
        <ProviderFields
          provider={provider}
          config={config}
          updateProviderConfig={updateProviderConfig}
        />
      </div>

      <div className="btn-row">
        <button className="btn btn-primary" type="button" onClick={onSave}>
          <Icon name="check" size={16} color="#fff" /> Save connection
        </button>
        {state.connected && !isDefault && (
          <button className="btn btn-secondary" type="button" onClick={onMakeDefault}>
            Make default
          </button>
        )}
        {isDefault && (
          <span className="default-note">
            <Icon name="circle-check" size={15} color="var(--blue-600)" /> Default
            provider
          </span>
        )}
      </div>
    </div>
  );
}

function ProviderFields({ provider, config, updateProviderConfig }) {
  const settingsKey = provider.settingsKey;

  if (provider.kind === "bedrock") {
    const useSavedAccessKey = Boolean(
      config.accessKeyConfigured && !config.clearAccessKey
    );
    const useSavedSecretKey = Boolean(
      config.secretKeyConfigured && !config.clearSecretKey
    );
    const canLoadBedrockModels = Boolean(config.region ?? DEFAULT_AWS_REGION)
      && (Boolean((config.accessKey ?? "").trim()) || useSavedAccessKey)
      && (Boolean((config.secretKey ?? "").trim()) || useSavedSecretKey);
    return (
      <>
        <Field
          label="AWS Region"
          hint="The region where you've enabled Bedrock model access."
        >
          <ProviderRegionSelect
            value={config.region ?? DEFAULT_AWS_REGION}
            onChange={(region) => updateProviderConfig(settingsKey, { region })}
          />
        </Field>
        <AwsSecretField
          label="Access key ID"
          type="text"
          placeholder="AKIA..."
          hint="An IAM key with Bedrock model-list and invoke permissions."
          value={config.accessKey ?? ""}
          configured={Boolean(config.accessKeyConfigured)}
          lastFour={config.accessKeyLastFour ?? ""}
          clear={Boolean(config.clearAccessKey)}
          onChange={(accessKey) =>
            updateProviderConfig(settingsKey, { accessKey, clearAccessKey: false })
          }
          onClear={() =>
            updateProviderConfig(settingsKey, {
              accessKey: "",
              accessKeyConfigured: false,
              accessKeyLastFour: "",
              clearAccessKey: true
            })
          }
        />
        <AwsSecretField
          label="Secret access key"
          type="password"
          placeholder="AWS secret access key"
          hint="Stored encrypted on this device - never synced."
          value={config.secretKey ?? ""}
          configured={Boolean(config.secretKeyConfigured)}
          clear={Boolean(config.clearSecretKey)}
          onChange={(secretKey) =>
            updateProviderConfig(settingsKey, { secretKey, clearSecretKey: false })
          }
          onClear={() =>
            updateProviderConfig(settingsKey, {
              secretKey: "",
              secretKeyConfigured: false,
              clearSecretKey: true
            })
          }
        />
        <div className="field" role="group" aria-label="Bedrock model">
          <span className="field-label">Model</span>
          <ProviderModelSelect
            ariaLabel="Bedrock model"
            idleHint="Enter access key ID and secret access key to load available Bedrock models."
            errorLabel="Unable to load Bedrock models."
            loadingLabel="Loading Bedrock models..."
            loadModels={provider.loadModels}
            lookupReady={canLoadBedrockModels}
            lookupValue={config.accessKey ?? ""}
            provider={config.region ?? DEFAULT_AWS_REGION}
            secretValue={config.secretKey ?? ""}
            useSavedKey={useSavedAccessKey}
            useSavedSecretKey={useSavedSecretKey}
            value={config.model ?? ""}
            onChange={(model) => updateProviderConfig(settingsKey, { model })}
          />
          <input
            className="input"
            type="text"
            aria-label="Bedrock model ID"
            placeholder="Model ID"
            value={config.model ?? ""}
            onChange={(event) =>
              updateProviderConfig(settingsKey, { model: event.target.value })
            }
          />
          <span className="field-hint">
            The list shows on-demand text foundation models in this region. Type a model ID manually for inference profiles, Marketplace endpoints, provisioned throughput, or custom/imported models.
          </span>
        </div>
      </>
    );
  }


  if (provider.kind === "local") {
    return (
      <>
        <Field label="Base URL" hint="Where your local model server is listening.">
          <input
            className="input"
            type="text"
            placeholder="http://localhost:11434"
            value={config.baseUrl ?? ""}
            onChange={(event) =>
              updateProviderConfig(settingsKey, { baseUrl: event.target.value })
            }
          />
        </Field>
        <Field label="Model" hint="Pick a local model to use for this provider.">
          <ProviderModelSelect
            errorLabel="Unable to load Ollama models."
            loadingLabel="Loading Ollama models…"
            loadModels={loadOllamaModels}
            lookupValue={config.baseUrl ?? ""}
            useSavedKey={false}
            value={config.model ?? ""}
            onChange={(model) => updateProviderConfig(settingsKey, { model })}
          />
        </Field>
      </>
    );
  }

  if (provider.kind === "endpoint") {
    const useSavedKey = Boolean(config.apiKeyConfigured && !config.clearApiKey);
    return (
      <>
        <Field
          label="Azure endpoint"
          hint="The base URL of your Azure OpenAI resource."
        >
          <input
            className="input"
            type="text"
            placeholder="https://resource-name.openai.azure.com"
            value={config.endpoint ?? ""}
            onChange={(event) =>
              updateProviderConfig(settingsKey, { endpoint: event.target.value })
            }
          />
        </Field>
        <ApiKeyField
          label="API key"
          placeholder="Azure OpenAI API key"
          config={config}
          onChange={(apiKey) =>
            updateProviderConfig(settingsKey, { apiKey, clearApiKey: false })
          }
          onClear={() => updateProviderConfig(settingsKey, clearedApiKey())}
        />
        {/* Two controls (select + text), so this is a group, not a single
            label. Each control carries its own aria-label. */}
        <div className="field" role="group" aria-label="Deployment">
          <span className="field-label">Deployment</span>
          <ProviderModelSelect
            ariaLabel="Deployment"
            errorLabel="Unable to load Azure OpenAI deployments."
            loadingLabel="Loading Azure OpenAI deployments…"
            loadModels={loadAzureDeployments}
            lookupValue={config.apiKey ?? ""}
            provider={config.endpoint ?? ""}
            useSavedKey={useSavedKey}
            value={config.deploymentName ?? ""}
            onChange={(deploymentName) =>
              updateProviderConfig(settingsKey, { deploymentName })
            }
          />
          <input
            className="input"
            type="text"
            aria-label="Deployment name"
            placeholder="Deployment name"
            value={config.deploymentName ?? ""}
            onChange={(event) =>
              updateProviderConfig(settingsKey, {
                deploymentName: event.target.value
              })
            }
          />
          <span className="field-hint">
            Pick a deployment, or type its name below if the list can’t load.
          </span>
        </div>
      </>
    );
  }

  // api-key providers
  const useSavedKey = Boolean(config.apiKeyConfigured && !config.clearApiKey);
  return (
    <>
      <ApiKeyField
        label={`${provider.name} API key`}
        placeholder={KEY_HINTS[provider.id] ?? "Paste your API key"}
        config={config}
        onChange={(apiKey) =>
          updateProviderConfig(settingsKey, { apiKey, clearApiKey: false })
        }
        onClear={() => updateProviderConfig(settingsKey, clearedApiKey())}
      />
      <Field label="Model">
        <ProviderModelSelect
          idleHint="Connect the provider to load available models."
          errorLabel={`Unable to load ${provider.name} models.`}
          loadingLabel={`Loading ${provider.name} models…`}
          loadModels={provider.loadModels}
          lookupValue={config.apiKey ?? ""}
          provider={provider.modelProvider}
          useSavedKey={useSavedKey}
          value={config.model ?? ""}
          onChange={(model) => updateProviderConfig(settingsKey, { model })}
        />
      </Field>
    </>
  );
}

function ToolStatus({ enabled }) {
  return enabled ? (
    <span className="badge badge-success">
      <span className="dot" />
      Active
    </span>
  ) : (
    <span className="badge badge-neutral">Off</span>
  );
}

function Switch({ checked, onChange, label }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      className={checked ? "switch on" : "switch"}
      onClick={() => onChange(!checked)}
    >
      <span className="switch-knob" />
    </button>
  );
}

function ToolsOverview({ settings, onOpen }) {
  return (
    <div className="settings-section wide">
      <div className="settings-intro">
        <h2 className="ds-h3">Tools</h2>
        <p className="ds-body">
          Capabilities the agent can call while it works. Open a tool to turn it
          on or off.
        </p>
      </div>
      <div className="providers-grid">
        {TOOLS.map((tool) => (
          <button
            key={tool.id}
            className="provider-card"
            type="button"
            onClick={() => onOpen(tool.id)}
          >
            <div className="provider-card-head">
              <span className="provider-icon local">
                <Icon name={tool.icon} size={19} color="var(--coffee-700)" />
              </span>
              <div className="provider-id">
                <div className="provider-name">{tool.name}</div>
                <div className="tool-summary">{tool.summary}</div>
              </div>
            </div>
            <div className="provider-card-foot">
              <ToolStatus enabled={toolEnabled(tool, settings)} />
              <span className="provider-configure">
                Configure
                <Icon name="chevron-right" size={13} color="var(--blue-600)" />
              </span>
            </div>
          </button>
        ))}
      </div>
    </div>
  );
}

function ToolConfig({ tool, enabled, onBack, onToggle }) {
  return (
    <div className="settings-section">
      <button className="config-back" type="button" onClick={onBack}>
        <Icon name="arrow-left" size={15} color="var(--gray-600)" /> All tools
      </button>

      <div className="config-head">
        <span className="provider-icon local lg">
          <Icon name={tool.icon} size={22} color="var(--coffee-700)" />
        </span>
        <div className="provider-id">
          <h2 className="ds-h3">{tool.name}</h2>
          <div className="provider-region">{tool.summary}</div>
        </div>
        <ToolStatus enabled={enabled} />
      </div>

      <div className="ds-card tool-config-card">
        <div className="tool-enable-row">
          <div className="tool-enable-text">
            <div className="tool-enable-title">
              {enabled ? "Tool is active" : "Tool is off"}
            </div>
            <div className="tool-enable-desc">
              {enabled
                ? `The agent can use ${tool.name} in every conversation.`
                : `Turn this on to let the agent use ${tool.name}.`}
            </div>
          </div>
          <Switch
            checked={enabled}
            onChange={onToggle}
            label={`${tool.name} tool`}
          />
        </div>
        <div className="tool-about">
          <div className="ds-overline">What it does</div>
          <p className="tool-about-text">{tool.description}</p>
        </div>
      </div>

      <div className="knowledge-privacy">
        <Icon name="shield-check" size={14} color="var(--success-600)" />
        Runs locally on this device, sandboxed to the folders you grant.
      </div>
    </div>
  );
}

function GeneralSettings({ draft, setDraft, onSave }) {
  return (
    <div className="settings-section">
      <div className="settings-intro">
        <h2 className="ds-h3">General</h2>
        <p className="ds-body">How the agent presents itself and behaves by default.</p>
      </div>
      <div className="ds-card" style={{ display: "flex", flexDirection: "column", gap: 18 }}>
        <Field
          label="Agent name"
          hint="Shown in the workspace and in drafts it prepares."
        >
          <input
            className="input"
            type="text"
            value={draft.agentName ?? ""}
            onChange={(event) => setDraft({ ...draft, agentName: event.target.value })}
          />
        </Field>
        <Field
          label="Default model provider"
          hint="Used for new conversations unless a project overrides it."
        >
          <select
            className="input"
            value={draft.aiModel ?? "none"}
            onChange={(event) => setDraft({ ...draft, aiModel: event.target.value })}
          >
            <option value="none">None</option>
            {PROVIDERS.map((provider) => {
              const connected = providerState(provider, draft).connected;
              return (
                <option key={provider.id} value={provider.aiModelValue} disabled={!connected}>
                  {provider.name}
                  {connected ? "" : " — not connected"}
                </option>
              );
            })}
          </select>
        </Field>
        <Field
          label="Custom instructions"
          hint="Always-on guidance. Project instructions stack on top of this."
        >
          <textarea
            className="input"
            rows={5}
            value={draft.customInstructions ?? ""}
            onChange={(event) =>
              setDraft({ ...draft, customInstructions: event.target.value })
            }
          />
        </Field>
      </div>
      <div className="btn-row">
        <button className="btn btn-primary" type="button" onClick={onSave}>
          <Icon name="check" size={16} color="#fff" /> Save changes
        </button>
      </div>
    </div>
  );
}

function Field({ label, hint, children }) {
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      {children}
      {hint && <span className="field-hint">{hint}</span>}
    </label>
  );
}

function ApiKeyField({ label, placeholder, config, onChange, onClear }) {
  const hasSavedKey = config?.apiKeyConfigured && !config?.clearApiKey;
  const lastFour = config?.apiKeyLastFour ? ` ending ${config.apiKeyLastFour}` : "";

  // The saved-key row contains a Clear <button>, which must not live inside the
  // <label>. The label wraps only the input; the row and hints are siblings.
  return (
    <div className="field">
      <label className="field">
        <span className="field-label">{label}</span>
        <span className="input-icon">
          <Icon name="key" size={16} />
          <input
            className="input"
            type="password"
            placeholder={hasSavedKey ? `Saved key${lastFour}` : placeholder}
            value={config?.apiKey ?? ""}
            onChange={(event) => onChange(event.target.value)}
          />
        </span>
      </label>
      {hasSavedKey ? (
        <span className="saved-key-row">
          <span className="saved-key-text">
            <Icon name="circle-check" size={13} color="var(--success-600)" />
            Saved key{lastFour}
          </span>
          <button className="btn btn-ghost btn-sm" type="button" onClick={onClear}>
            Clear
          </button>
        </span>
      ) : (
        <span className="field-hint">
          Stored encrypted on this device — never synced.
        </span>
      )}
      {config?.clearApiKey && (
        <span className="field-hint">Key will be cleared when you save.</span>
      )}
    </div>
  );
}

function AwsSecretField({
  label,
  type,
  placeholder,
  hint,
  value,
  configured,
  lastFour,
  clear,
  onChange,
  onClear
}) {
  const hasSavedValue = configured && !clear;
  const ending = lastFour ? ` ending ${lastFour}` : "";

  return (
    <div className="field">
      <label className="field">
        <span className="field-label">{label}</span>
        <span className="input-icon">
          <Icon name="key" size={16} />
          <input
            className="input"
            type={type}
            placeholder={hasSavedValue ? `Saved value${ending}` : placeholder}
            value={value}
            onChange={(event) => onChange(event.target.value)}
          />
        </span>
      </label>
      {hasSavedValue ? (
        <span className="saved-key-row">
          <span className="saved-key-text">
            <Icon name="circle-check" size={13} color="var(--success-600)" />
            Saved value{ending}
          </span>
          <button className="btn btn-ghost btn-sm" type="button" onClick={onClear}>
            Clear
          </button>
        </span>
      ) : (
        <span className="field-hint">{hint}</span>
      )}
      {clear && <span className="field-hint">Value will be cleared when you save.</span>}
    </div>
  );
}

function ProviderRegionSelect({ value, onChange }) {
  const [regions, setRegions] = useState([]);
  const [status, setStatus] = useState("loading");

  useEffect(() => {
    let isActive = true;
    async function loadRegions() {
      try {
        const loadedRegions = await getBedrockRegions();
        if (!isActive) {
          return;
        }
        setRegions(loadedRegions);
        setStatus("loaded");
      } catch (error) {
        if (!isActive) {
          return;
        }
        console.error("Region lookup failed:", error);
        setRegions([]);
        setStatus("error");
      }
    }
    loadRegions();
    return () => {
      isActive = false;
    };
  }, []);

  const options = regions.includes(value) ? regions : [value, ...regions].filter(Boolean);

  return (
    <>
      <select
        className="input"
        disabled={options.length === 0}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        {options.length === 0 ? (
          <option value="">{status === "loading" ? "Loading regions..." : "-"}</option>
        ) : (
          options.map((region) => (
            <option key={region} value={region}>
              {AWS_REGION_LABELS[region]
                ? `${region} — ${AWS_REGION_LABELS[region]}`
                : region}
            </option>
          ))
        )}
      </select>
      {status === "error" && (
        <p className="model-select-status">Unable to load Bedrock regions.</p>
      )}
    </>
  );
}


function ProviderModelSelect({
  ariaLabel,
  idleHint,
  errorLabel,
  loadingLabel,
  loadModels,
  lookupReady,
  lookupValue,
  onChange,
  provider,
  secretValue,
  useSavedKey,
  useSavedSecretKey,
  value
}) {
  const [models, setModels] = useState([]);
  const [status, setStatus] = useState("idle");
  const [message, setMessage] = useState("");
  // Bumped by the Retry button to re-run the load effect after a failure.
  const [retryNonce, setRetryNonce] = useState(0);

  useEffect(() => {
    let isActive = true;
    const canLookup = lookupReady ?? Boolean(lookupValue || useSavedKey);
    if (!canLookup) {
      setModels([]);
      setStatus("idle");
      setMessage("");
      return () => {
        isActive = false;
      };
    }

    const timeout = window.setTimeout(
      async () => {
        setStatus("loading");
        setMessage("");
        try {
          const loadedModels = await loadModels({
            lookupValue,
            apiKey: lookupValue,
            provider,
            secretValue,
            useSavedKey,
            useSavedSecretKey
          });
          if (!isActive) {
            return;
          }
          setModels(loadedModels);
          setStatus("loaded");
        } catch (error) {
          if (!isActive) {
            return;
          }
          setModels([]);
          setStatus("error");
          // Surface a friendly label, not the raw backend error body.
          console.error("Model lookup failed:", error);
          setMessage(errorLabel);
        }
      },
      lookupValue || secretValue ? 500 : 0
    );

    return () => {
      isActive = false;
      window.clearTimeout(timeout);
    };
  }, [
    errorLabel,
    loadModels,
    lookupReady,
    lookupValue,
    provider,
    retryNonce,
    secretValue,
    useSavedKey,
    useSavedSecretKey
  ]);

  const options = [...models];
  const selectedValue = options.includes(value) ? value : "";

  // A failed fetch replaces the (empty) select with a red box + Retry, rather
  // than a disabled dropdown beside a gray error line.
  if (status === "error") {
    return (
      <div className="model-select-error-wrap">
        <div className="input model-select-error">
          <Icon name="alert-circle" size={15} color="var(--danger-600)" />
          <span>{message || errorLabel}</span>
        </div>
        <button
          className="btn btn-secondary btn-sm model-select-retry"
          type="button"
          onClick={() => {
            // Flip to loading immediately so the click registers visibly; the
            // effect (keyed on retryNonce) then re-runs the lookup.
            setStatus("loading");
            setRetryNonce((nonce) => nonce + 1);
          }}
        >
          <Icon name="refresh-cw" size={14} color="var(--gray-700)" /> Retry
        </button>
      </div>
    );
  }

  return (
    <>
      <select
        className="input"
        aria-label={ariaLabel}
        disabled={options.length === 0}
        value={selectedValue}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="">{status === "loading" ? loadingLabel : "—"}</option>
        {options.map((model) => (
          <option key={model} value={model}>
            {model}
          </option>
        ))}
      </select>
      {status === "idle" && idleHint && (
        <p className="model-select-status">{idleHint}</p>
      )}
    </>
  );
}

function clearedApiKey() {
  return {
    apiKey: "",
    apiKeyConfigured: false,
    apiKeyLastFour: "",
    clearApiKey: true
  };
}
