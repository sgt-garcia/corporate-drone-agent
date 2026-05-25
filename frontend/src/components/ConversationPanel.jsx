import { useEffect, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";

export function ConversationPanel({
  conversation,
  messages,
  onDraftChange,
  onReload,
  onSave,
  onSend,
  project,
  value
}) {
  const historyRef = useRef(null);
  const [settingsDraft, setSettingsDraft] = useState(conversation);

  useEffect(() => {
    setSettingsDraft(conversation);
  }, [conversation]);

  useEffect(() => {
    if (historyRef.current) {
      historyRef.current.scrollTop = historyRef.current.scrollHeight;
    }
  }, [conversation.id, messages]);

  function submitMessage() {
    onSend(value);
  }

  function handleKeyDown(event) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      submitMessage();
    }
  }

  return (
    <section className="conversation-page" aria-label={`${conversation.name} conversation`}>
      <details className="conversation-settings">
        <summary>Conversation settings</summary>
        <div className="conversation-settings-grid">
          <label>
            Name
            <input
              type="text"
              value={settingsDraft.name ?? ""}
              onChange={(event) =>
                setSettingsDraft({ ...settingsDraft, name: event.target.value })
              }
            />
          </label>
          <label>
            Custom instructions
            <textarea
              rows="3"
              value={settingsDraft.settings?.customInstructions ?? ""}
              onChange={(event) =>
                setSettingsDraft({
                  ...settingsDraft,
                  settings: {
                    ...(settingsDraft.settings ?? {}),
                    customInstructions: event.target.value
                  }
                })
              }
            />
          </label>
          <div className="conversation-settings-actions">
            <button type="button" onClick={onReload}>
              Reload
            </button>
            <button type="button" onClick={() => onSave(settingsDraft)}>
              Save
            </button>
          </div>
        </div>
      </details>

      <div className="message-history" aria-label="Message history" ref={historyRef}>
        {messages.map((message) => (
          <article className={`chat-message ${message.role}`} key={message.id}>
            <div className="message-author">
              {formatMessageTimestamp(message.createdAt)}
            </div>
            <div className="message-bubble">
              {message.role === "status" ? (
                <span>{message.content}</span>
              ) : (
                <ReactMarkdown>{message.content}</ReactMarkdown>
              )}
            </div>
          </article>
        ))}
      </div>

      <form
        className="message-composer"
        onSubmit={(event) => {
          event.preventDefault();
          submitMessage();
        }}
      >
        <textarea
          aria-label={`Message ${conversation.name}`}
          onChange={(event) => onDraftChange(event.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={`Message ${project.name}`}
          rows="3"
          value={value}
        />
        <button type="submit" disabled={!value.trim()}>
          Send
        </button>
      </form>
    </section>
  );
}

function formatMessageTimestamp(value) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}
