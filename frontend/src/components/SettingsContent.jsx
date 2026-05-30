import { EuFlagIcon } from "./EuFlagIcon.jsx";
import { ProviderLabel } from "./ProviderLabel.jsx";
import { SettingsScreen } from "./SettingsScreen.jsx";
import {
  getAnthropicModels,
  getAzureOpenAiDeployments,
  getGoogleGeminiModels,
  getMistralModels,
  getOllamaModels,
  getOpenAiModels
} from "../api.js";
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
          <ProviderModelSelect
            errorLabel="Unable to load OpenAI models."
            lookupValue={draft.openAi?.apiKey ?? ""}
            loadModels={getOpenAiModels}
            loadingLabel="Loading OpenAI models..."
            provider="openai"
            useSavedKey={Boolean(draft.openAi?.apiKeyConfigured && !draft.openAi?.clearApiKey)}
            value={draft.openAi?.model ?? ""}
            onChange={(model) =>
              setDraft({
                ...draft,
                openAi: { ...(draft.openAi ?? {}), model }
              })
            }
          />
        </label>
      </SettingsScreen>
    );
  }

  if (activeSettingsItem === "OpenAI (Official SDK)") {
    return (
      <SettingsScreen
        title="OpenAI (Official SDK)"
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
          settings={draft.openAiOfficialSdk}
          onChange={(apiKey) =>
            setDraft({
              ...draft,
              openAiOfficialSdk: {
                ...(draft.openAiOfficialSdk ?? {}),
                apiKey,
                clearApiKey: false
              }
            })
          }
          onClear={() =>
            setDraft({
              ...draft,
              openAiOfficialSdk: clearedApiKeySettings(draft.openAiOfficialSdk)
            })
          }
        />
        <label>
          OpenAI Model
          <ProviderModelSelect
            errorLabel="Unable to load OpenAI models."
            lookupValue={draft.openAiOfficialSdk?.apiKey ?? ""}
            loadModels={getOpenAiModels}
            loadingLabel="Loading OpenAI models..."
            provider="openai-official-sdk"
            useSavedKey={Boolean(
              draft.openAiOfficialSdk?.apiKeyConfigured && !draft.openAiOfficialSdk?.clearApiKey
            )}
            value={draft.openAiOfficialSdk?.model ?? ""}
            onChange={(model) =>
              setDraft({
                ...draft,
                openAiOfficialSdk: {
                  ...(draft.openAiOfficialSdk ?? {}),
                  model
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
          Azure OpenAI Deployment
          <ProviderModelSelect
            errorLabel="Unable to load Azure OpenAI deployments."
            lookupValue={draft.azureOpenAi?.apiKey ?? ""}
            loadModels={loadAzureOpenAiDeployments}
            loadingLabel="Loading Azure OpenAI deployments..."
            provider={draft.azureOpenAi?.endpoint ?? ""}
            useSavedKey={Boolean(draft.azureOpenAi?.apiKeyConfigured && !draft.azureOpenAi?.clearApiKey)}
            value=""
            onChange={(deploymentName) =>
              setDraft({
                ...draft,
                azureOpenAi: {
                  ...(draft.azureOpenAi ?? {}),
                  deploymentName
                }
              })
            }
          />
        </label>
        <label>
          Azure OpenAI Deployment Name
          <input
            type="text"
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
          <ProviderModelSelect
            errorLabel="Unable to load Ollama models."
            lookupValue={draft.ollama?.baseUrl ?? ""}
            loadModels={loadOllamaModels}
            loadingLabel="Loading Ollama models..."
            useSavedKey={false}
            value={draft.ollama?.model ?? ""}
            onChange={(model) =>
              setDraft({
                ...draft,
                ollama: {
                  ...(draft.ollama ?? {}),
                  model
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
        title={<ProviderLabel name="Mistral AI" />}
        titleLabel="Mistral AI"
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
          <ProviderModelSelect
            errorLabel="Unable to load Mistral models."
            lookupValue={draft.mistralAi?.apiKey ?? ""}
            loadModels={getMistralModels}
            loadingLabel="Loading Mistral models..."
            useSavedKey={Boolean(draft.mistralAi?.apiKeyConfigured && !draft.mistralAi?.clearApiKey)}
            value={draft.mistralAi?.model ?? ""}
            onChange={(model) =>
              setDraft({
                ...draft,
                mistralAi: {
                  ...(draft.mistralAi ?? {}),
                  model
                }
              })
            }
          />
        </label>
      </SettingsScreen>
    );
  }

  if (activeSettingsItem === "Google Gemini") {
    return (
      <SettingsScreen
        title="Google Gemini"
        subtitle="Settings"
        onReload={() => {
          setDraft(settings);
          onReload();
        }}
        onSave={() => onSave(draft)}
      >
        <ApiKeyField
          label="Google Gemini API Key"
          placeholder="Google Gemini API key"
          settings={draft.googleGemini}
          onChange={(apiKey) =>
            setDraft({
              ...draft,
              googleGemini: {
                ...(draft.googleGemini ?? {}),
                apiKey,
                clearApiKey: false
              }
            })
          }
          onClear={() =>
            setDraft({
              ...draft,
              googleGemini: clearedApiKeySettings(draft.googleGemini)
            })
          }
        />
        <label>
          Google Gemini Model
          <ProviderModelSelect
            errorLabel="Unable to load Google Gemini models."
            lookupValue={draft.googleGemini?.apiKey ?? ""}
            loadModels={getGoogleGeminiModels}
            loadingLabel="Loading Google Gemini models..."
            useSavedKey={Boolean(draft.googleGemini?.apiKeyConfigured && !draft.googleGemini?.clearApiKey)}
            value={draft.googleGemini?.model ?? ""}
            onChange={(model) =>
              setDraft({
                ...draft,
                googleGemini: {
                  ...(draft.googleGemini ?? {}),
                  model
                }
              })
            }
          />
        </label>
      </SettingsScreen>
    );
  }

  if (activeSettingsItem === "Anthropic") {
    return (
      <SettingsScreen
        title="Anthropic"
        subtitle="Settings"
        onReload={() => {
          setDraft(settings);
          onReload();
        }}
        onSave={() => onSave(draft)}
      >
        <ApiKeyField
          label="Anthropic API Key"
          placeholder="Anthropic API key"
          settings={draft.anthropic}
          onChange={(apiKey) =>
            setDraft({
              ...draft,
              anthropic: {
                ...(draft.anthropic ?? {}),
                apiKey,
                clearApiKey: false
              }
            })
          }
          onClear={() =>
            setDraft({
              ...draft,
              anthropic: clearedApiKeySettings(draft.anthropic)
            })
          }
        />
        <label>
          Anthropic Model
          <ProviderModelSelect
            errorLabel="Unable to load Anthropic models."
            lookupValue={draft.anthropic?.apiKey ?? ""}
            loadModels={getAnthropicModels}
            loadingLabel="Loading Anthropic models..."
            useSavedKey={Boolean(draft.anthropic?.apiKeyConfigured && !draft.anthropic?.clearApiKey)}
            value={draft.anthropic?.model ?? ""}
            onChange={(model) =>
              setDraft({
                ...draft,
                anthropic: {
                  ...(draft.anthropic ?? {}),
                  model
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
        <span className="select-with-provider-icon">
          {draft.aiModel === "mistral-ai" && <EuFlagIcon className="select-provider-icon" />}
          <select
            className={draft.aiModel === "mistral-ai" ? "has-provider-icon" : undefined}
            value={draft.aiModel ?? "none"}
            onChange={(event) => setDraft({ ...draft, aiModel: event.target.value })}
          >
            <option value="none">None</option>
            <option value="mistral-ai">Mistral AI</option>
            <option value="anthropic">Anthropic</option>
            <option value="azure-openai">Azure OpenAI</option>
            <option value="google-gemini">Google Gemini</option>
            <option value="ollama">Ollama</option>
            <option value="openai">OpenAI</option>
            <option value="openai-official-sdk">OpenAI (Official SDK)</option>
          </select>
        </span>
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

function loadOllamaModels({ lookupValue }) {
  return getOllamaModels({ baseUrl: lookupValue });
}

function loadAzureOpenAiDeployments({ lookupValue, provider, useSavedKey }) {
  return getAzureOpenAiDeployments({ apiKey: lookupValue, endpoint: provider, useSavedKey });
}

function ProviderModelSelect({
  errorLabel,
  lookupValue,
  loadModels,
  loadingLabel,
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

    const timeout = window.setTimeout(async () => {
      setStatus("loading");
      setMessage("");

      try {
        const loadedModels = await loadModels({ lookupValue, apiKey: lookupValue, provider, useSavedKey });
        if (!isActive) {
          return;
        }
        setModels(loadedModels);
        setStatus("loaded");
        setMessage("");
      } catch (error) {
        if (!isActive) {
          return;
        }
        setModels([]);
        setStatus("error");
        setMessage(error.message || errorLabel);
      }
    }, lookupValue ? 500 : 0);

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
        disabled={options.length === 0}
        value={selectedValue}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="">{status === "loading" ? loadingLabel : ""}</option>
        {options.map((model) => (
          <option key={model} value={model}>
            {model}
          </option>
        ))}
      </select>
      {message && <p className="model-select-status">{message}</p>}
    </>
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
