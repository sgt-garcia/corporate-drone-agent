import { SettingsScreen } from "./SettingsScreen.jsx";
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
        titleLabel="OpenAI"
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

  if (activeSettingsItem === "OpenAI (SDK)") {
    return (
      <SettingsScreen
        title="OpenAI (SDK)"
        titleLabel="OpenAI (SDK)"
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
          settings={draft.openAiSdk}
          onChange={(apiKey) =>
            setDraft({
              ...draft,
              openAiSdk: {
                ...(draft.openAiSdk ?? {}),
                apiKey,
                clearApiKey: false
              }
            })
          }
          onClear={() =>
            setDraft({
              ...draft,
              openAiSdk: clearedApiKeySettings(draft.openAiSdk)
            })
          }
        />
        <label>
          OpenAI Model
          <ProviderModelSelect
            errorLabel="Unable to load OpenAI models."
            lookupValue={draft.openAiSdk?.apiKey ?? ""}
            loadModels={getOpenAiModels}
            loadingLabel="Loading OpenAI models..."
            provider="openai-sdk"
            useSavedKey={Boolean(
              draft.openAiSdk?.apiKeyConfigured && !draft.openAiSdk?.clearApiKey
            )}
            value={draft.openAiSdk?.model ?? ""}
            onChange={(model) =>
              setDraft({
                ...draft,
                openAiSdk: {
                  ...(draft.openAiSdk ?? {}),
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
        titleLabel="Azure OpenAI"
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
            value={draft.azureOpenAi?.deploymentName ?? ""}
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
        titleLabel="Ollama"
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

  if (activeSettingsItem === "Mistral") {
    return (
      <SettingsScreen
        title="Mistral"
        titleLabel="Mistral"
        subtitle="Settings"
        onReload={() => {
          setDraft(settings);
          onReload();
        }}
        onSave={() => onSave(draft)}
      >
        <ApiKeyField
          label="Mistral API Key"
          placeholder="Mistral API key"
          settings={draft.mistral}
          onChange={(apiKey) =>
            setDraft({
              ...draft,
              mistral: {
                ...(draft.mistral ?? {}),
                apiKey,
                clearApiKey: false
              }
            })
          }
          onClear={() =>
            setDraft({
              ...draft,
              mistral: clearedApiKeySettings(draft.mistral)
            })
          }
        />
        <label>
          Mistral Model
          <ProviderModelSelect
            errorLabel="Unable to load Mistral models."
            lookupValue={draft.mistral?.apiKey ?? ""}
            loadModels={getMistralModels}
            loadingLabel="Loading Mistral models..."
            useSavedKey={Boolean(draft.mistral?.apiKeyConfigured && !draft.mistral?.clearApiKey)}
            value={draft.mistral?.model ?? ""}
            onChange={(model) =>
              setDraft({
                ...draft,
                mistral: {
                  ...(draft.mistral ?? {}),
                  model
                }
              })
            }
          />
        </label>
      </SettingsScreen>
    );
  }

  if (activeSettingsItem === "Gemini") {
    return (
      <SettingsScreen
        title="Gemini"
        titleLabel="Gemini"
        subtitle="Settings"
        onReload={() => {
          setDraft(settings);
          onReload();
        }}
        onSave={() => onSave(draft)}
      >
        <ApiKeyField
          label="Gemini API Key"
          placeholder="Gemini API key"
          settings={draft.gemini}
          onChange={(apiKey) =>
            setDraft({
              ...draft,
              gemini: {
                ...(draft.gemini ?? {}),
                apiKey,
                clearApiKey: false
              }
            })
          }
          onClear={() =>
            setDraft({
              ...draft,
              gemini: clearedApiKeySettings(draft.gemini)
            })
          }
        />
        <label>
          Gemini Model
          <ProviderModelSelect
            errorLabel="Unable to load Gemini models."
            lookupValue={draft.gemini?.apiKey ?? ""}
            loadModels={getGeminiModels}
            loadingLabel="Loading Gemini models..."
            useSavedKey={Boolean(draft.gemini?.apiKeyConfigured && !draft.gemini?.clearApiKey)}
            value={draft.gemini?.model ?? ""}
            onChange={(model) =>
              setDraft({
                ...draft,
                gemini: {
                  ...(draft.gemini ?? {}),
                  model
                }
              })
            }
          />
        </label>
      </SettingsScreen>
    );
  }

  if (activeSettingsItem === "Groq") {
    return (
      <SettingsScreen
        title="Groq"
        titleLabel="Groq"
        subtitle="Settings"
        onReload={() => {
          setDraft(settings);
          onReload();
        }}
        onSave={() => onSave(draft)}
      >
        <ApiKeyField
          label="Groq API Key"
          placeholder="Groq API key"
          settings={draft.groq}
          onChange={(apiKey) =>
            setDraft({
              ...draft,
              groq: {
                ...(draft.groq ?? {}),
                apiKey,
                clearApiKey: false
              }
            })
          }
          onClear={() =>
            setDraft({
              ...draft,
              groq: clearedApiKeySettings(draft.groq)
            })
          }
        />
        <label>
          Groq Model
          <ProviderModelSelect
            errorLabel="Unable to load Groq models."
            lookupValue={draft.groq?.apiKey ?? ""}
            loadModels={getGroqModels}
            loadingLabel="Loading Groq models..."
            useSavedKey={Boolean(draft.groq?.apiKeyConfigured && !draft.groq?.clearApiKey)}
            value={draft.groq?.model ?? ""}
            onChange={(model) =>
              setDraft({
                ...draft,
                groq: {
                  ...(draft.groq ?? {}),
                  model
                }
              })
            }
          />
        </label>
      </SettingsScreen>
    );
  }

  if (activeSettingsItem === "DeepSeek") {
    return (
      <SettingsScreen
        title="DeepSeek"
        titleLabel="DeepSeek"
        subtitle="Settings"
        onReload={() => {
          setDraft(settings);
          onReload();
        }}
        onSave={() => onSave(draft)}
      >
        <ApiKeyField
          label="DeepSeek API Key"
          placeholder="DeepSeek API key"
          settings={draft.deepSeek}
          onChange={(apiKey) =>
            setDraft({
              ...draft,
              deepSeek: {
                ...(draft.deepSeek ?? {}),
                apiKey,
                clearApiKey: false
              }
            })
          }
          onClear={() =>
            setDraft({
              ...draft,
              deepSeek: clearedApiKeySettings(draft.deepSeek)
            })
          }
        />
        <label>
          DeepSeek Model
          <ProviderModelSelect
            errorLabel="Unable to load DeepSeek models."
            lookupValue={draft.deepSeek?.apiKey ?? ""}
            loadModels={getDeepSeekModels}
            loadingLabel="Loading DeepSeek models..."
            useSavedKey={Boolean(draft.deepSeek?.apiKeyConfigured && !draft.deepSeek?.clearApiKey)}
            value={draft.deepSeek?.model ?? ""}
            onChange={(model) =>
              setDraft({
                ...draft,
                deepSeek: {
                  ...(draft.deepSeek ?? {}),
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
        titleLabel="Anthropic"
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
        Model provider
        <select
          value={draft.aiModel ?? "none"}
          onChange={(event) => setDraft({ ...draft, aiModel: event.target.value })}
        >
          <option value="none">None</option>
          <option value="anthropic">Anthropic</option>
          <option value="azure-openai">Azure OpenAI</option>
          <option value="deepseek">DeepSeek</option>
          <option value="gemini">Gemini</option>
          <option value="groq">Groq</option>
          <option value="mistral">Mistral</option>
          <option value="ollama">Ollama</option>
          <option value="openai">OpenAI</option>
          <option value="openai-sdk">OpenAI (SDK)</option>
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
