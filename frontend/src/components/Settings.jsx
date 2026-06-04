import { useEffect, useState } from "react";
import { Icon } from "./Icon.jsx";
import {
  getAnthropicModels,
  getAzureOpenAiDeployments,
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
  { id: "knowledge", label: "Knowledge", icon: "database" }
];

// Maximum number of continuously-scanned local folders.
const KNOWLEDGE_MAX = 10;

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
  return {
    connected: Boolean(config.apiKeyConfigured),
    model: config.model ?? ""
  };
}

export function Settings({
  onClose,
  settings,
  onSave,
  knowledgeFolders,
  onAddKnowledgeFolder,
  onRemoveKnowledgeFolder,
  onScanKnowledgeFolder,
  onToggleKnowledgeFolderPause
}) {
  const [draft, setDraft] = useState(settings);
  const [section, setSection] = useState("general");
  const [openProviderId, setOpenProviderId] = useState(null);
  const [knowledgeView, setKnowledgeView] = useState(null); // null | "local-folders"

  useEffect(() => {
    setDraft(settings);
  }, [settings]);

  const openProvider = PROVIDERS.find((p) => p.id === openProviderId) ?? null;

  function selectSection(id) {
    setSection(id);
    setOpenProviderId(null);
    setKnowledgeView(null);
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
                onAddFolder={onAddKnowledgeFolder}
                onRemoveFolder={onRemoveKnowledgeFolder}
                onScanFolder={onScanKnowledgeFolder}
                onTogglePause={onToggleKnowledgeFolderPause}
                onBack={() => setKnowledgeView(null)}
              />
            ) : (
              <KnowledgeOverview
                folders={knowledgeFolders}
                onOpen={() => setKnowledgeView("local-folders")}
              />
            ))}
        </div>
      </div>
    </div>
  );
}

function FolderStatus({ status }) {
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
  return (
    <span className="badge badge-success">
      <span className="dot" />
      Scanned
    </span>
  );
}

function KnowledgeOverview({ folders, onOpen }) {
  const scanning = folders.filter((f) => f.status === "scanning").length;
  const summary = scanning
    ? `${scanning} scanning now`
    : folders.length
      ? "Auto-scanning · up to date"
      : "No folders yet";

  return (
    <div className="settings-section wide">
      <div className="settings-intro">
        <h2 className="ds-h3">Knowledge</h2>
        <p className="ds-body">
          Sources the agent draws on to understand your work context. Everything
          is indexed locally on this device.
        </p>
      </div>
      <div className="providers-grid">
        <button className="provider-card" type="button" onClick={onOpen}>
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
          <div className="provider-card-foot">
            <span className="folder-summary">
              <Icon name="circle-dot" size={12} color="var(--gray-400)" />
              {summary}
            </span>
            <span className="provider-configure">
              Manage
              <Icon name="chevron-right" size={13} color="var(--blue-600)" />
            </span>
          </div>
        </button>
      </div>
    </div>
  );
}

function FolderRow({
  folder,
  confirming,
  onScanNow,
  onTogglePause,
  onRequestRemove,
  onCancelRemove,
  onConfirmRemove
}) {
  const files = Number(folder.files ?? 0).toLocaleString();
  const size = folder.size || "not scanned yet";
  const nextScan = folder.nextScan ? ` · next scan ${folder.nextScan}` : "";
  const meta =
    folder.status === "paused"
      ? `Paused · ${files} files · ${size}`
      : folder.status === "scanning"
        ? `${files} files · ${size} · scanning now`
        : `${files} files · ${size}${nextScan}`;
  const paused = folder.status === "paused";

  return (
    <div className="folder-row">
      <Icon name="folder" size={18} color="var(--gray-500)" />
      <div className="folder-row-id">
        <div className="folder-path">{folder.path}</div>
        <div className="folder-meta">{meta}</div>
      </div>
      {confirming ? (
        <div className="folder-confirm">
          <span className="folder-confirm-prompt">Remove folder?</span>
          <button
            className="btn btn-secondary btn-sm"
            type="button"
            onClick={onCancelRemove}
          >
            Cancel
          </button>
          <button
            className="btn btn-danger btn-sm"
            type="button"
            onClick={onConfirmRemove}
          >
            Remove
          </button>
        </div>
      ) : (
        <div className="folder-row-controls">
          <FolderStatus status={folder.status} />
          <button
            className="iconbtn"
            type="button"
            title="Scan now"
            aria-label="Scan now"
            onClick={onScanNow}
            disabled={folder.status !== "scanned"}
          >
            <Icon name="refresh-cw" size={16} color="var(--gray-500)" />
          </button>
          <button
            className="iconbtn"
            type="button"
            title={paused ? "Resume scanning" : "Pause scanning"}
            aria-label={paused ? "Resume scanning" : "Pause scanning"}
            onClick={onTogglePause}
          >
            <Icon
              name={paused ? "play" : "pause"}
              size={16}
              color="var(--gray-500)"
            />
          </button>
          <button
            className="iconbtn"
            type="button"
            title="Remove folder"
            aria-label="Remove folder"
            onClick={onRequestRemove}
          >
            <Icon name="trash" size={16} color="var(--gray-500)" />
          </button>
        </div>
      )}
    </div>
  );
}

function LocalFoldersConfig({
  folders,
  onAddFolder,
  onRemoveFolder,
  onScanFolder,
  onTogglePause,
  onBack
}) {
  const [draft, setDraft] = useState("");
  const [confirmId, setConfirmId] = useState(null);
  const [error, setError] = useState("");
  const [checking, setChecking] = useState(false);

  async function addFolder() {
    const path = draft.trim();
    if (checking) {
      return;
    }
    setError("");
    setChecking(true);
    try {
      await onAddFolder(path);
      setDraft("");
    } catch (error) {
      setError(error.message || "Could not add that folder.");
    } finally {
      setChecking(false);
    }
  }

  async function removeFolder(id) {
    try {
      await onRemoveFolder(id);
      setConfirmId(null);
    } catch (error) {
      setError(error.message || "Could not remove that folder.");
    }
  }

  async function scanNow(id) {
    try {
      await onScanFolder(id);
    } catch (error) {
      setError(error.message || "Could not scan that folder.");
    }
  }

  async function togglePause(id) {
    const folder = folders.find((item) => item.id === id);
    if (!folder) {
      return;
    }
    try {
      await onTogglePause(folder);
    } catch (error) {
      setError(error.message || "Could not update that folder.");
    }
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
      </div>

      <div className="ds-card folders-card">
        <div className="folder-add">
          <div className="folder-add-row">
            <span className="folder-count">
              {folders.length}{" "}
              <span className="folder-count-max">/ {KNOWLEDGE_MAX} folders</span>
            </span>
            <div className="folder-add-controls">
              <span className="input-icon">
                <Icon name="folder" size={16} />
                <input
                  className={error ? "input has-error" : "input"}
                  type="text"
                  placeholder="Add a folder path…"
                  value={draft}
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
                disabled={checking}
              >
                {checking ? (
                  <>
                    <Icon
                      name="refresh-cw"
                      size={15}
                      color="#fff"
                      className="cda-spin"
                    />
                    Checking…
                  </>
                ) : (
                  <>
                    <Icon name="plus" size={15} color="#fff" /> Add
                  </>
                )}
              </button>
            </div>
          </div>
          {error && (
            <div className="folder-add-error">
              <Icon name="alert-triangle" size={14} color="var(--danger-600)" />
              <span>{error}</span>
            </div>
          )}
        </div>

        {folders.length === 0 ? (
          <div className="folder-empty">
            No folders yet. Add one above to start building local context.
          </div>
        ) : (
          folders.map((folder) => (
            <FolderRow
              key={folder.id}
              folder={folder}
              confirming={confirmId === folder.id}
              onScanNow={() => scanNow(folder.id)}
              onTogglePause={() => togglePause(folder.id)}
              onRequestRemove={() => setConfirmId(folder.id)}
              onCancelRemove={() => setConfirmId(null)}
              onConfirmRemove={() => removeFolder(folder.id)}
            />
          ))
        )}
      </div>

      <div className="knowledge-privacy">
        <Icon name="shield-check" size={14} color="var(--success-600)" />
        Indexing runs on device. Nothing is uploaded — only the agent on this
        machine can read it.
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
        placeholder="sk-…"
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

function ProviderModelSelect({
  ariaLabel,
  idleHint,
  errorLabel,
  loadingLabel,
  loadModels,
  lookupValue,
  onChange,
  provider,
  useSavedKey,
  value
}) {
  const [models, setModels] = useState([]);
  const [status, setStatus] = useState("idle");
  const [message, setMessage] = useState("");

  useEffect(() => {
    let isActive = true;
    if (!lookupValue && !useSavedKey) {
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
            useSavedKey
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
      lookupValue ? 500 : 0
    );

    return () => {
      isActive = false;
      window.clearTimeout(timeout);
    };
  }, [errorLabel, loadModels, lookupValue, provider, useSavedKey]);

  const options = [...models];
  const selectedValue = options.includes(value) ? value : "";

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
      {message && <p className="model-select-status">{message}</p>}
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
