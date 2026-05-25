import { SettingsScreen } from "./SettingsScreen.jsx";

export function ProjectSettingsPanel({ project }) {
  return (
    <SettingsScreen title={project.name} subtitle="Project settings">
      <label>
        Project name
        <input type="text" defaultValue={project.name} />
      </label>
      <label>
        Working folder
        <input type="text" placeholder="Optional local folder path" />
      </label>
      <label>
        Custom instructions
        <textarea
          defaultValue="Use this project context when answering questions about planning, decisions, and follow-up work."
          rows="8"
        />
      </label>
    </SettingsScreen>
  );
}
