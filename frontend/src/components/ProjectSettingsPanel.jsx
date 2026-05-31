import { useEffect, useState } from "react";
import { Icon } from "./Icon.jsx";

export function ProjectSettingsPanel({ onSave, project }) {
  const [draft, setDraft] = useState(project);

  useEffect(() => {
    setDraft(project);
  }, [project]);

  return (
    <div className="project-settings">
      <div className="project-settings-inner">
        <div className="project-settings-head">
          <span className="provider-icon lg">
            <Icon name="folder" size={20} color="var(--blue-600)" />
          </span>
          <div>
            <span className="ds-overline">Project</span>
            <h2 className="ds-h2">{draft.name}</h2>
          </div>
        </div>

        <div className="ds-card" style={{ display: "flex", flexDirection: "column", gap: 18 }}>
          <label className="field">
            <span className="field-label">Project name</span>
            <input
              className="input"
              type="text"
              value={draft.name ?? ""}
              onChange={(event) => setDraft({ ...draft, name: event.target.value })}
            />
          </label>
          <label className="field">
            <span className="field-label">Working folder</span>
            <span className="input-icon">
              <Icon name="folder" size={16} />
              <input
                className="input"
                type="text"
                placeholder="Optional local folder path"
                value={draft.workingFolder ?? ""}
                onChange={(event) =>
                  setDraft({ ...draft, workingFolder: event.target.value })
                }
              />
            </span>
            <span className="field-hint">
              Local folder the agent may read from for this project.
            </span>
          </label>
          <label className="field">
            <span className="field-label">Custom instructions</span>
            <textarea
              className="input"
              rows={5}
              value={draft.customInstructions ?? ""}
              onChange={(event) =>
                setDraft({ ...draft, customInstructions: event.target.value })
              }
            />
            <span className="field-hint">
              Context the agent uses for every conversation in this project.
            </span>
          </label>
        </div>

        <div className="btn-row">
          <button className="btn btn-primary" type="button" onClick={() => onSave(draft)}>
            <Icon name="check" size={16} color="#fff" /> Save changes
          </button>
        </div>
      </div>
    </div>
  );
}
