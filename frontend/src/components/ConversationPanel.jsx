import { useEffect, useRef } from "react";
import ReactMarkdown from "react-markdown";
import { Icon } from "./Icon.jsx";
import { Logomark } from "./Logomark.jsx";

export function ConversationPanel({
  conversation,
  echoMode = false,
  isLoaded = true,
  messages,
  onDraftChange,
  onOpenProviders,
  onRetry,
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

  // The last agent turn owns the Retry affordance — only the most recent reply
  // can be re-run, mirroring the design's error card.
  const lastAgentMessageId = findLastAgentMessageId(messages);

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
          messages.map((message) => (
            <Turn
              key={message.id}
              message={message}
              echoMode={echoMode}
              onRetry={message.id === lastAgentMessageId ? onRetry : undefined}
            />
          ))
        )}
      </div>

      <div className="composer-wrap">
        {echoMode && <NoProviderBanner onOpenProviders={onOpenProviders} />}
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

// No model provider connected: the backend silently echoes the user's message,
// so surface a recoverable nudge above the composer (matches the design).
function NoProviderBanner({ onOpenProviders }) {
  return (
    <div className="no-provider-banner">
      <Icon name="alert-triangle" size={17} color="var(--warning-600)" />
      <span className="no-provider-text">
        <b>No model provider connected.</b> Replies just echo your message until
        you connect one.
      </span>
      <button
        className="btn btn-primary btn-sm"
        type="button"
        onClick={onOpenProviders}
      >
        <Icon name="cpu" size={14} color="#fff" /> Connect a provider
      </button>
    </div>
  );
}

function Turn({ message, echoMode = false, onRetry }) {
  const timestamp = formatTimestamp(message.createdAt);

  if (message.role === "user") {
    return (
      <article className="turn turn--user">
        <span className="avatar-user" aria-hidden="true">
          <Icon name="user" size={16} color="#fff" />
        </span>
        <div className="turn-body">
          <div className="turn-author">You</div>
          <div className="turn-text turn-text--plain">{message.content}</div>
          {timestamp && <div className="turn-time">{timestamp}</div>}
        </div>
      </article>
    );
  }

  const isStatus = message.role === "status";
  const isError = message.role === "error";

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
        ) : isError ? (
          <AgentError errorText={message.content} onRetry={onRetry} />
        ) : (
          <>
            <div className="turn-text turn-text--markdown">
              <ReactMarkdown>{message.content}</ReactMarkdown>
            </div>
            {echoMode && (
              <div className="echo-note">
                <span className="badge badge-warning">
                  <Icon name="alert-triangle" size={12} color="var(--warning-700)" />
                  Echo mode
                </span>
                <span className="echo-note-text">
                  No model connected — your message was echoed back.
                </span>
              </div>
            )}
            {timestamp && <div className="turn-time">{timestamp}</div>}
          </>
        )}
      </div>
    </article>
  );
}

function AgentError({ errorText, onRetry }) {
  return (
    <div className="agent-error" role="alert">
      <Icon name="alert-circle" size={18} color="var(--danger-600)" />
      <div className="agent-error-body">
        <div className="agent-error-title">Reply failed</div>
        <div className="agent-error-text">
          {errorText || "The agent couldn’t complete this reply."}
        </div>
        {onRetry && (
          <button
            className="btn btn-secondary btn-sm"
            type="button"
            onClick={onRetry}
          >
            <Icon name="refresh-cw" size={14} color="var(--gray-700)" /> Retry
          </button>
        )}
      </div>
    </div>
  );
}

function findLastAgentMessageId(messages) {
  for (let i = messages.length - 1; i >= 0; i -= 1) {
    const role = messages[i].role;
    if (role === "assistant" || role === "error") {
      return messages[i].id;
    }
  }
  return null;
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
