import { useEffect, useRef } from "react";
import ReactMarkdown from "react-markdown";

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
