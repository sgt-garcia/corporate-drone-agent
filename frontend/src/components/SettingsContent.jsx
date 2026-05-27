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
        <label>
          OpenAI API Key
          <input
            type="password"
            placeholder="sk-..."
            value={draft.openAi?.apiKey ?? ""}
            onChange={(event) =>
              setDraft({
                ...draft,
                openAi: { ...(draft.openAi ?? {}), apiKey: event.target.value }
              })
            }
          />
        </label>
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
        <label>
          OpenAI API Key
          <input
            type="password"
            placeholder="sk-..."
            value={draft.openAiOfficial?.apiKey ?? ""}
            onChange={(event) =>
              setDraft({
                ...draft,
                openAiOfficial: {
                  ...(draft.openAiOfficial ?? {}),
                  apiKey: event.target.value
                }
              })
            }
          />
        </label>
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
        <label>
          Azure OpenAI API Key
          <input
            type="password"
            placeholder="Azure OpenAI API key"
            value={draft.azureOpenAi?.apiKey ?? ""}
            onChange={(event) =>
              setDraft({
                ...draft,
                azureOpenAi: {
                  ...(draft.azureOpenAi ?? {}),
                  apiKey: event.target.value
                }
              })
            }
          />
        </label>
        <label>
          Azure OpenAI Deployment Name
          <input
            type="text"
            placeholder="deployment-name"
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
