import { useEffect, useRef } from "react";
import ReactMarkdown from "react-markdown";
import { Icon } from "./Icon.jsx";
import { Logomark } from "./Logomark.jsx";

export function ConversationPanel({
  conversation,
  isLoaded = true,
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

  // Show the design's greeting until the conversation has actually started —
  // i.e. until the user has sent a message. Gated on isLoaded so the greeting
  // never flashes while messages are still being fetched.
  const isEmpty = isLoaded && !messages.some((message) => message.role === "user");

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
  const timestamp = formatTimestamp(message.createdAt);

  if (message.role === "user") {
    return (
      <article className="turn turn--user">
        <span className="avatar-user" aria-hidden="true">
          <Icon name="user" size={16} color="#fff" />
        </span>
        <div className="turn-body">
          <div className="turn-author">You</div>
          <div className="turn-text">{message.content}</div>
          {timestamp && <div className="turn-time">{timestamp}</div>}
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
          <div className="streaming-dots" aria-label="Working">
            {[0, 1, 2].map((i) => (
              <span
                key={i}
                className="cda-pulse"
                style={{ animationDelay: `${i * 0.18}s` }}
              />
            ))}
          </div>
        ) : (
          <>
            <div className="turn-text">
              <ReactMarkdown>{message.content}</ReactMarkdown>
            </div>
            {timestamp && <div className="turn-time">{timestamp}</div>}
          </>
        )}
      </div>
    </article>
  );
}

// ISO date + 24-hour time (e.g. "2026-05-31 09:00"), per the design's
// metric/ISO convention. A single space separates the date and time.
function formatTimestamp(createdAt) {
  if (!createdAt) {
    return "";
  }
  const date = new Date(createdAt);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  const day = date.toLocaleDateString("en-CA");
  const time = date.toLocaleTimeString("en-GB", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  });
  return `${day} ${time}`;
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
