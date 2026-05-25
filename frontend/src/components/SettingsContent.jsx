import { SettingsScreen } from "./SettingsScreen.jsx";

export function SettingsContent({ activeSettingsItem }) {
  if (activeSettingsItem === "OpenAI") {
    return (
      <SettingsScreen title="OpenAI" subtitle="Settings">
        <label>
          OpenAI API Key
          <input type="password" placeholder="sk-..." />
        </label>
        <label>
          OpenAI Model
          <input type="text" defaultValue="gpt-4.1-mini" />
        </label>
      </SettingsScreen>
    );
  }

  if (activeSettingsItem === "Azure OpenAI") {
    return (
      <SettingsScreen title="Azure OpenAI" subtitle="Settings">
        <label>
          Azure OpenAI Endpoint
          <input type="text" placeholder="https://resource-name.openai.azure.com" />
        </label>
        <label>
          Azure OpenAI API Key
          <input type="password" placeholder="Azure OpenAI API key" />
        </label>
        <label>
          Azure OpenAI Deployment Name
          <input type="text" placeholder="deployment-name" />
        </label>
      </SettingsScreen>
    );
  }

  return (
    <SettingsScreen title="General" subtitle="Settings">
      <label>
        Agent name
        <input type="text" defaultValue="Corporate Drone Agent" />
      </label>
      <label>
        AI model
        <select defaultValue="none">
          <option value="none">None</option>
          <option value="openai">OpenAI</option>
          <option value="azure-openai">Azure OpenAI</option>
        </select>
      </label>
      <label>
        Custom instructions
        <textarea
          defaultValue="Answer with concise, practical guidance using available local project context first."
          rows="8"
        />
      </label>
    </SettingsScreen>
  );
}
