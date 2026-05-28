import { SettingsScreen } from "./SettingsScreen.jsx";
import { useEffect, useState } from "react";

export function SettingsContent({ activeSettingsItem, onReload, onSave, settings }) {
  const [draft, setDraft] = useState(settings);

  useEffect(() => {
    setDraft(settings);
  }, [settings]);

  if (activeSettingsItem === "OpenAI") {
    return (
      <SettingsScreen
        title="OpenAI"
        subtitle="Settings"
        onReload={() => {
          setDraft(settings);
          onReload();
        }}
        onSave={() => onSave(draft)}
      >
        <ApiKeyField
          label="OpenAI API Key"
          placeholder="sk-..."
          settings={draft.openAi}
          onChange={(apiKey) =>
            setDraft({
              ...draft,
              openAi: { ...(draft.openAi ?? {}), apiKey, clearApiKey: false }
            })
          }
          onClear={() =>
            setDraft({
              ...draft,
              openAi: clearedApiKeySettings(draft.openAi)
            })
          }
        />
        <label>
          OpenAI Model
          <input
            type="text"
            value={draft.openAi?.model ?? ""}
            onChange={(event) =>
              setDraft({
                ...draft,
                openAi: { ...(draft.openAi ?? {}), model: event.target.value }
              })
            }
          />
        </label>
      </SettingsScreen>
    );
  }

  if (activeSettingsItem === "OpenAI (Official)") {
    return (
      <SettingsScreen
        title="OpenAI (Official)"
        subtitle="Settings"
        onReload={() => {
          setDraft(settings);
          onReload();
        }}
        onSave={() => onSave(draft)}
      >
        <ApiKeyField
          label="OpenAI API Key"
          placeholder="sk-..."
          settings={draft.openAiOfficial}
          onChange={(apiKey) =>
            setDraft({
              ...draft,
              openAiOfficial: {
                ...(draft.openAiOfficial ?? {}),
                apiKey,
                clearApiKey: false
              }
            })
          }
          onClear={() =>
            setDraft({
              ...draft,
              openAiOfficial: clearedApiKeySettings(draft.openAiOfficial)
            })
          }
        />
        <label>
          OpenAI Model
          <input
            type="text"
            value={draft.openAiOfficial?.model ?? ""}
            onChange={(event) =>
              setDraft({
                ...draft,
                openAiOfficial: {
                  ...(draft.openAiOfficial ?? {}),
                  model: event.target.value
                }
              })
            }
          />
        </label>
      </SettingsScreen>
    );
  }

  if (activeSettingsItem === "Azure OpenAI") {
    return (
      <SettingsScreen
        title="Azure OpenAI"
        subtitle="Settings"
        onReload={() => {
          setDraft(settings);
          onReload();
        }}
        onSave={() => onSave(draft)}
      >
        <label>
          Azure OpenAI Endpoint
          <input
            type="text"
            placeholder="https://resource-name.openai.azure.com"
            value={draft.azureOpenAi?.endpoint ?? ""}
            onChange={(event) =>
              setDraft({
                ...draft,
                azureOpenAi: {
                  ...(draft.azureOpenAi ?? {}),
                  endpoint: event.target.value
                }
              })
            }
          />
        </label>
        <ApiKeyField
          label="Azure OpenAI API Key"
          placeholder="Azure OpenAI API key"
          settings={draft.azureOpenAi}
          onChange={(apiKey) =>
            setDraft({
              ...draft,
              azureOpenAi: {
                ...(draft.azureOpenAi ?? {}),
                apiKey,
                clearApiKey: false
              }
            })
          }
          onClear={() =>
            setDraft({
              ...draft,
              azureOpenAi: clearedApiKeySettings(draft.azureOpenAi)
            })
          }
        />
        <label>
          Azure OpenAI Deployment Name
          <input
            type="text"
            placeholder="gpt-5.5"
            value={draft.azureOpenAi?.deploymentName ?? ""}
            onChange={(event) =>
              setDraft({
                ...draft,
                azureOpenAi: {
                  ...(draft.azureOpenAi ?? {}),
                  deploymentName: event.target.value
                }
              })
            }
          />
        </label>
      </SettingsScreen>
    );
  }

  if (activeSettingsItem === "Ollama") {
    return (
      <SettingsScreen
        title="Ollama"
        subtitle="Settings"
        onReload={() => {
          setDraft(settings);
          onReload();
        }}
        onSave={() => onSave(draft)}
      >
        <label>
          Ollama Base URL
          <input
            type="text"
            placeholder="http://localhost:11434"
            value={draft.ollama?.baseUrl ?? ""}
            onChange={(event) =>
              setDraft({
                ...draft,
                ollama: {
                  ...(draft.ollama ?? {}),
                  baseUrl: event.target.value
                }
              })
            }
          />
        </label>
        <label>
          Ollama Model
          <input
            type="text"
            placeholder="gemma4"
            value={draft.ollama?.model ?? ""}
            onChange={(event) =>
              setDraft({
                ...draft,
                ollama: {
                  ...(draft.ollama ?? {}),
                  model: event.target.value
                }
              })
            }
          />
        </label>
      </SettingsScreen>
    );
  }

  if (activeSettingsItem === "Mistral AI") {
    return (
      <SettingsScreen
        title="Mistral AI"
        subtitle="Settings"
        onReload={() => {
          setDraft(settings);
          onReload();
        }}
        onSave={() => onSave(draft)}
      >
        <ApiKeyField
          label="Mistral AI API Key"
          placeholder="Mistral AI API key"
          settings={draft.mistralAi}
          onChange={(apiKey) =>
            setDraft({
              ...draft,
              mistralAi: {
                ...(draft.mistralAi ?? {}),
                apiKey,
                clearApiKey: false
              }
            })
          }
          onClear={() =>
            setDraft({
              ...draft,
              mistralAi: clearedApiKeySettings(draft.mistralAi)
            })
          }
        />
        <label>
          Mistral AI Model
          <input
            type="text"
            placeholder="mistral-small"
            value={draft.mistralAi?.model ?? ""}
            onChange={(event) =>
              setDraft({
                ...draft,
                mistralAi: {
                  ...(draft.mistralAi ?? {}),
                  model: event.target.value
                }
              })
            }
          />
        </label>
      </SettingsScreen>
    );
  }

  return (
    <SettingsScreen
      title="General"
      subtitle="Settings"
      onReload={() => {
        setDraft(settings);
        onReload();
      }}
      onSave={() => onSave(draft)}
    >
      <label>
        Agent name
        <input
          type="text"
          value={draft.agentName ?? ""}
          onChange={(event) => setDraft({ ...draft, agentName: event.target.value })}
        />
      </label>
      <label>
        AI model
        <select
          value={draft.aiModel ?? "none"}
          onChange={(event) => setDraft({ ...draft, aiModel: event.target.value })}
        >
          <option value="none">None</option>
          <option value="openai">OpenAI</option>
          <option value="openai-official">OpenAI (Official)</option>
          <option value="azure-openai">Azure OpenAI</option>
          <option value="ollama">Ollama</option>
          <option value="mistral-ai">Mistral AI</option>
        </select>
      </label>
      <label>
        Custom instructions
        <textarea
          value={draft.customInstructions ?? ""}
          onChange={(event) =>
            setDraft({ ...draft, customInstructions: event.target.value })
          }
          rows="8"
        />
      </label>
    </SettingsScreen>
  );
}

function ApiKeyField({ label, onChange, onClear, placeholder, settings }) {
  const hasSavedKey = settings?.apiKeyConfigured && !settings?.clearApiKey;
  const lastFour = settings?.apiKeyLastFour ? ` ending ${settings.apiKeyLastFour}` : "";

  return (
    <div className="secret-field">
      <label>
        {label}
        <input
          type="password"
          placeholder={hasSavedKey ? `Saved key${lastFour}` : placeholder}
          value={settings?.apiKey ?? ""}
          onChange={(event) => onChange(event.target.value)}
        />
      </label>
      {hasSavedKey && (
        <div className="secret-status">
          <span>Saved key{lastFour}</span>
          <button type="button" onClick={onClear}>
            Clear
          </button>
        </div>
      )}
      {settings?.clearApiKey && (
        <p className="secret-status-text">Key will be cleared when you save.</p>
      )}
    </div>
  );
}

function clearedApiKeySettings(settings) {
  return {
    ...(settings ?? {}),
    apiKey: "",
    apiKeyConfigured: false,
    apiKeyLastFour: "",
    clearApiKey: true
  };
}
