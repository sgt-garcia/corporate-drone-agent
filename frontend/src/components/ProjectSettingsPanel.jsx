import { SettingsScreen } from "./SettingsScreen.jsx";
import { useEffect, useState } from "react";

export function ProjectSettingsPanel({ onSave, project }) {
  const [draft, setDraft] = useState(project);

  useEffect(() => {
    setDraft(project);
  }, [project]);

  return (
    <SettingsScreen
      title={draft.name}
      subtitle="Project settings"
      onReload={() => setDraft(project)}
      onSave={() => onSave(draft)}
    >
      <label>
        Project name
        <input
          type="text"
          value={draft.name}
          onChange={(event) => setDraft({ ...draft, name: event.target.value })}
        />
      </label>
      <label>
        Working folder
        <input
          type="text"
          placeholder="Optional local folder path"
          value={draft.workingFolder ?? ""}
          onChange={(event) => setDraft({ ...draft, workingFolder: event.target.value })}
        />
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
