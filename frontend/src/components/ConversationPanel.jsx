import { useEffect, useRef, useState } from "react";
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
  onRegenerate,
  onSend,
  project,
  value
}) {
  const historyRef = useRef(null);

  // Show the design's greeting until the conversation has actually started —
  // i.e. until the user has sent a message. Gated on isLoaded so the greeting
  // never flashes while messages are still being fetched.
  const isEmpty = isLoaded && !messages.some((message) => message.role === "user");

  useEffect(() => {
    const container = historyRef.current;
    if (!container || isEmpty || messages.length === 0) {
      return;
    }
    const last = messages[messages.length - 1];
    const lastEl = container.lastElementChild;
    // For agent replies (incl. while the status indicator is up), land the
    // reader at the TOP of the latest turn rather than the absolute bottom —
    // long replies are taller than the viewport, so bottom-scrolling drops you
    // mid-message and clips the tail behind the composer. Fall back to bottom
    // for a trailing user turn.
    if (last.role !== "user" && lastEl) {
      const top =
        lastEl.getBoundingClientRect().top -
        container.getBoundingClientRect().top +
        container.scrollTop;
      container.scrollTop = Math.max(0, top - 20);
    } else {
      container.scrollTop = container.scrollHeight;
    }
  }, [conversation.id, messages, isEmpty]);

  // The last agent turn owns the Retry / Regenerate affordances — only the most
  // recent reply can be re-run, mirroring the design's error card and toolbar.
  const lastAgentMessageId = findLastAgentMessageId(messages);
  // Regenerate is gated on the genuine last turn (not just the last agent turn):
  // while a regenerate is mid-flight the old reply is dropped before the new one
  // arrives, and gating on "last agent" would briefly surface the button on an
  // earlier reply. Gating on the final message keeps it pinned to the live turn.
  const lastMessageId = messages.length ? messages[messages.length - 1].id : null;
  // A reply is in flight while the transient "status" turn (thinking dots) is up;
  // Regenerate hides until it settles so we never stack reply requests.
  const busy = messages.some((message) => message.role === "status");

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
          renderThread(messages, {
            echoMode,
            onRetry,
            onRegenerate,
            lastAgentMessageId,
            lastMessageId,
            busy
          })
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

// Interleave day dividers between turns, once per calendar day, so a lived-in
// thread reads chronologically (Today / Yesterday / weekday / full date).
function renderThread(messages, turnProps) {
  const out = [];
  let lastKey = null;
  messages.forEach((message) => {
    const key = toDayKey(message.createdAt);
    if (key && key !== lastKey) {
      out.push(<DayDivider key={`day-${key}-${message.id}`} dayKey={key} />);
      lastKey = key;
    }
    const isLastAgentTurn = message.id === turnProps.lastAgentMessageId;
    const isLastTurn = message.id === turnProps.lastMessageId;
    out.push(
      <Turn
        key={message.id}
        message={message}
        echoMode={turnProps.echoMode}
        onRetry={isLastAgentTurn ? turnProps.onRetry : undefined}
        onRegenerate={
          isLastTurn && !turnProps.busy ? turnProps.onRegenerate : undefined
        }
      />
    );
  });
  return out;
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

function DayDivider({ dayKey }) {
  return (
    <div className="day-divider" title={dayKey}>
      <span className="day-divider-line" />
      <span className="day-divider-label">{dayLabel(dayKey)}</span>
      <span className="day-divider-line" />
    </div>
  );
}

function Turn({ message, echoMode = false, onRetry, onRegenerate }) {
  const timeLabel = formatTime(message.createdAt);
  const stamp = formatStamp(message.createdAt);

  if (message.role === "user") {
    return (
      <article className="turn turn--user">
        <span className="avatar-user" aria-hidden="true">
          <Icon name="user" size={17} color="var(--gray-500)" />
        </span>
        <div className="turn-body">
          <div className="turn-author">You</div>
          <div className="turn-text turn-text--plain">{message.content}</div>
          {timeLabel && (
            <div className="turn-time" title={stamp}>
              {timeLabel}
            </div>
          )}
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
            {echoMode ? (
              <>
                <div className="echo-note">
                  <span className="badge badge-warning">
                    <Icon
                      name="alert-triangle"
                      size={12}
                      color="var(--warning-700)"
                    />
                    Echo mode
                  </span>
                  <span className="echo-note-text">
                    No model connected — your message was echoed back.
                  </span>
                </div>
                {timeLabel && (
                  <div className="turn-time" title={stamp}>
                    {timeLabel}
                  </div>
                )}
              </>
            ) : (
              <>
                {message.sources?.length ? (
                  <SourcePanel sources={message.sources} />
                ) : null}
                <div className="agent-meta-row">
                  <AgentActions
                    content={message.content}
                    onRegenerate={onRegenerate}
                  />
                  <span className="agent-meta-spacer" />
                  {timeLabel && (
                    <span className="turn-time turn-time--inline" title={stamp}>
                      {timeLabel}
                    </span>
                  )}
                </div>
              </>
            )}
          </>
        )}
      </div>
    </article>
  );
}

// Client-side reply toolbar: Copy is local-only; Regenerate (shown on the last
// agent turn while idle) asks the backend to replace the reply in place.
function AgentActions({ content, onRegenerate }) {
  const [copied, setCopied] = useState(false);

  function copy() {
    if (!navigator.clipboard) {
      return;
    }
    navigator.clipboard
      .writeText(content)
      .then(() => {
        setCopied(true);
        setTimeout(() => setCopied(false), 1600);
      })
      .catch(() => {});
  }

  return (
    <div className="agent-actions">
      <button type="button" className="agent-act" onClick={copy}>
        <Icon
          name={copied ? "check" : "copy"}
          size={14}
          color={copied ? "var(--success-600)" : "var(--gray-500)"}
        />
        {copied ? "Copied" : "Copy"}
      </button>
      {onRegenerate && (
        <button type="button" className="agent-act" onClick={onRegenerate}>
          <Icon name="refresh-cw" size={14} color="var(--gray-500)" /> Regenerate
        </button>
      )}
    </div>
  );
}

// The agent's provenance surface: clickable pills for everything it read and
// every draft it produced, each expanding into an inline preview. Renders only
// when a reply carries a `sources` array — dormant until the backend supplies
// per-message provenance.
function SourcePanel({ sources }) {
  const [openId, setOpenId] = useState(null);
  if (!sources || !sources.length) {
    return null;
  }
  const draftCount = sources.filter((source) => source.kind === "draft").length;
  const active = sources.find((source) => source.id === openId) ?? null;

  return (
    <div className="sources">
      <div className="sources-head">
        <Icon name="shield-check" size={14} color="var(--gray-500)" />
        <span className="ds-overline">
          Sources
          {draftCount ? ` · ${draftCount} draft${draftCount > 1 ? "s" : ""}` : ""}
        </span>
      </div>
      <div className="source-pills">
        {sources.map((source) => (
          <SourcePill
            key={source.id}
            source={source}
            open={openId === source.id}
            onToggle={() =>
              setOpenId((current) => (current === source.id ? null : source.id))
            }
          />
        ))}
      </div>
      {active && (
        <SourcePreview source={active} onClose={() => setOpenId(null)} />
      )}
    </div>
  );
}

function SourcePill({ source, open, onToggle }) {
  return (
    <button
      type="button"
      className={open ? "source-pill is-open" : "source-pill"}
      aria-expanded={open}
      onClick={onToggle}
    >
      <span
        className="source-pill-icon"
        style={{ background: tintForKind(source.kind) }}
        aria-hidden="true"
      >
        <Icon name={source.icon} size={14} color={accentForKind(source.kind)} />
      </span>
      <span className="source-pill-id">
        <span className="source-pill-title">{source.title}</span>
        <span className="source-pill-meta">{source.meta}</span>
      </span>
      <Icon
        name="chevron-down"
        size={14}
        color="var(--gray-400)"
        className={open ? "source-pill-caret is-open" : "source-pill-caret"}
      />
    </button>
  );
}

function SourcePreview({ source, onClose }) {
  const preview = source.preview ?? {};
  return (
    <div className="source-preview">
      <div className="source-preview-head">
        <Icon name={source.icon} size={15} color={accentForKind(source.kind)} />
        <div className="source-preview-id">
          <div className="source-preview-title">{source.title}</div>
          <div className="source-preview-meta">{source.meta}</div>
        </div>
        <button
          className="iconbtn"
          type="button"
          onClick={onClose}
          aria-label="Close source preview"
        >
          <Icon name="x" size={15} color="var(--gray-500)" />
        </button>
      </div>
      <div className="source-preview-body">
        {source.kind === "draft" ? (
          <>
            <div className="source-preview-fields">
              <span className="source-preview-field-label">To</span>
              <span className="source-preview-field-value">{preview.to}</span>
              <span className="source-preview-field-label">Subject</span>
              <span className="source-preview-subject">{preview.subject}</span>
            </div>
            <div className="source-preview-draft-body">{preview.body}</div>
          </>
        ) : (
          <div className="source-preview-excerpt">{preview.excerpt}</div>
        )}
      </div>
      {(source.kind === "draft" || source.url) && (
        <div className="source-preview-foot">
          {source.kind === "draft" && (
            <span className="badge badge-warning">
              <span className="dot" /> Not sent
            </span>
          )}
          <span className="source-preview-foot-spacer" />
          {source.url && (
            <a
              className="source-link"
              href={source.url}
              target="_blank"
              rel="noreferrer"
            >
              {source.actionLabel || "Open"}{" "}
              <Icon name="external-link" size={13} color="var(--blue-600)" />
            </a>
          )}
        </div>
      )}
    </div>
  );
}

function accentForKind(kind) {
  if (kind === "draft") {
    return "var(--coffee-500)";
  }
  if (kind === "doc") {
    return "var(--blue-600)";
  }
  return "var(--gray-500)";
}

function tintForKind(kind) {
  if (kind === "draft") {
    return "var(--coffee-100)";
  }
  if (kind === "doc") {
    return "var(--blue-50)";
  }
  return "var(--gray-100)";
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

// ISO date key (YYYY-MM-DD, local) used to group a thread into calendar days.
function toDayKey(createdAt) {
  if (!createdAt) {
    return "";
  }
  const date = new Date(createdAt);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  return date.toLocaleDateString("en-CA");
}

// Friendly label for a day divider: Today / Yesterday / weekday / full date.
function dayLabel(key) {
  if (!key) {
    return "";
  }
  const date = new Date(`${key}T00:00:00`);
  if (Number.isNaN(date.getTime())) {
    return key;
  }
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const diff = Math.round((today - date) / 86400000);
  if (diff === 0) {
    return "Today";
  }
  if (diff === 1) {
    return "Yesterday";
  }
  if (diff > 1 && diff < 7) {
    return date.toLocaleDateString("en-US", { weekday: "long" });
  }
  return date.toLocaleDateString("en-US", {
    weekday: "long",
    month: "long",
    day: "numeric"
  });
}

// 24-hour HH:MM, per the design's metric/ISO convention. The full
// "YYYY-MM-DD HH:MM" stamp rides along as a hover title.
function formatTime(createdAt) {
  if (!createdAt) {
    return "";
  }
  const date = new Date(createdAt);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  return date.toLocaleTimeString("en-GB", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  });
}

function formatStamp(createdAt) {
  if (!createdAt) {
    return "";
  }
  const date = new Date(createdAt);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  const day = date.toLocaleDateString("en-CA");
  return `${day} ${formatTime(createdAt)}`;
}

function EmptyGreeting({ projectName }) {
  const hour = new Date().getHours();
  const greeting =
    hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";

  return (
    <div className="empty-greeting">
      <span className="empty-greeting-logo">
        <span className="empty-greeting-glow" aria-hidden="true" />
        <Logomark size={46} radius={11} className="logomark" />
      </span>
      <h1 className="ds-h1">
        {greeting}
        <span className="greeting-accent">.</span>
      </h1>
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
