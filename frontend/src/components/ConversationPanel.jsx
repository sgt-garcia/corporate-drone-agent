import { useEffect, useRef } from "react";
import ReactMarkdown from "react-markdown";
import { Icon } from "./Icon.jsx";
import { Logomark } from "./Logomark.jsx";

export function ConversationPanel({
  conversation,
  messages,
  onDraftChange,
  onSend,
  project,
  value
}) {
  const historyRef = useRef(null);

  useEffect(() => {
    if (historyRef.current) {
      historyRef.current.scrollTop = historyRef.current.scrollHeight;
    }
  }, [conversation.id, messages]);

  const isEmpty = messages.length === 0;

  function submitMessage() {
    onSend(value);
  }

  return (
    <section className="conversation" aria-label={`${conversation.name} conversation`}>
      <div
        className={isEmpty ? "thread is-empty" : "thread"}
        aria-label="Message history"
        ref={historyRef}
      >
        {isEmpty ? (
          <EmptyGreeting projectName={project?.name} />
        ) : (
          messages.map((message) => <Turn key={message.id} message={message} />)
        )}
      </div>

      <div className="composer-wrap">
        <Composer
          placeholder={`Message ${project?.name ?? "your agent"}…`}
          value={value}
          onChange={onDraftChange}
          onSend={submitMessage}
        />
      </div>
    </section>
  );
}

function Turn({ message }) {
  if (message.role === "user") {
    return (
      <article className="turn">
        <span className="avatar-user" aria-hidden="true">
          <Icon name="user" size={16} color="#fff" />
        </span>
        <div className="turn-body">
          <div className="turn-author">You</div>
          <div className="turn-text">{message.content}</div>
        </div>
      </article>
    );
  }

  const isStatus = message.role === "status";

  return (
    <article className="turn">
      <Logomark size={30} />
      <div className="turn-body">
        <div className="turn-author">Agent</div>
        {isStatus ? (
          <div className="turn-status">
            <span className="streaming-dots" style={{ marginBottom: 6 }}>
              {[0, 1, 2].map((i) => (
                <span
                  key={i}
                  className="cda-pulse"
                  style={{ animationDelay: `${i * 0.18}s` }}
                />
              ))}
            </span>
            {message.content}
          </div>
        ) : (
          <div className="turn-text">
            <ReactMarkdown>{message.content}</ReactMarkdown>
          </div>
        )}
      </div>
    </article>
  );
}

function EmptyGreeting({ projectName }) {
  const hour = new Date().getHours();
  const greeting =
    hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";

  return (
    <div className="empty-greeting">
      <Logomark size={46} radius={11} className="logomark" />
      <h1 className="ds-h1">{greeting}.</h1>
      <p className="ds-body-lg">
        {projectName ? (
          <>
            Working in <span className="project-emphasis">{projectName}</span>.{" "}
          </>
        ) : null}
        Ask me any question.
      </p>
    </div>
  );
}

function Composer({ placeholder, value, onChange, onSend }) {
  const ref = useRef(null);
  const canSend = Boolean(value.trim());

  useEffect(() => {
    if (ref.current) {
      ref.current.style.height = "auto";
      ref.current.style.height = `${Math.min(ref.current.scrollHeight, 160)}px`;
    }
  }, [value]);

  function handleKeyDown(event) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      onSend();
    }
  }

  return (
    <div className="composer">
      <button
        className="composer-attach"
        type="button"
        disabled
        aria-disabled="true"
        aria-label="Attach (unavailable)"
        tabIndex={-1}
      >
        <Icon name="paperclip" size={18} color="var(--gray-500)" />
      </button>
      <textarea
        ref={ref}
        className="composer-input"
        rows={1}
        aria-label={placeholder}
        placeholder={placeholder}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        onKeyDown={handleKeyDown}
      />
      <button
        className={canSend ? "composer-send active" : "composer-send"}
        type="button"
        disabled={!canSend}
        aria-label="Send message"
        onClick={onSend}
      >
        <Icon name="arrow-up" size={19} color="#fff" strokeWidth={2.25} />
      </button>
    </div>
  );
}
