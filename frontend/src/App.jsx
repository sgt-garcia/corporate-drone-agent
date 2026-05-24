import "./styles.css";
import { useEffect, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";

const initialProjects = [
  {
    id: "northwind",
    name: "Northwind Migration",
    conversations: [
      { id: "northwind-kickoff", name: "Kickoff notes" },
      { id: "northwind-inventory", name: "Data inventory" },
      { id: "northwind-risks", name: "Risk review" }
    ]
  },
  {
    id: "atlas",
    name: "Atlas Reporting",
    conversations: [
      { id: "atlas-dashboard", name: "Dashboard scope" },
      { id: "atlas-finance", name: "Finance metrics" },
      { id: "atlas-release", name: "Release checklist" }
    ]
  },
  {
    id: "helix",
    name: "Helix Operations",
    conversations: [
      { id: "helix-status", name: "Weekly status" },
      { id: "helix-vendor", name: "Vendor follow-up" },
      { id: "helix-incident", name: "Incident summary" }
    ]
  }
];

const pages = {
  work: {
    title: "Work"
  },
  settings: {
    title: "Settings",
    menu: ["General", "Models", "Connectors", "Storage"]
  }
};

export default function App() {
  const [projects, setProjects] = useState(initialProjects);
  const [messagesByConversationId, setMessagesByConversationId] = useState(() =>
    createInitialMessages(initialProjects)
  );
  const [draftsByConversationId, setDraftsByConversationId] = useState({});
  const [activePage, setActivePage] = useState("work");
  const [activeWorkItemId, setActiveWorkItemId] = useState(
    initialProjects[0].conversations[0].id
  );
  const [collapsedProjectIds, setCollapsedProjectIds] = useState([]);
  const [activeSettingsItem, setActiveSettingsItem] = useState(pages.settings.menu[0]);

  const page = pages[activePage];
  const activeWorkItem = findWorkItem(projects, activeWorkItemId);
  const activeMenuItem = activePage === "work" ? activeWorkItem.name : activeSettingsItem;
  const pageContext =
    activePage === "work"
      ? activeWorkItem.type === "project"
        ? "Project settings"
        : "Conversation"
      : page.title;

  function addProject() {
    const project = {
      id: `project-${crypto.randomUUID()}`,
      name: "New Project",
      conversations: []
    };

    setProjects((currentProjects) => [...currentProjects, project]);
    setActiveWorkItemId(project.id);
  }

  function addConversation(projectId) {
    const targetProject = projects.find((project) => project.id === projectId);
    const conversation = {
      id: `conversation-${crypto.randomUUID()}`,
      name: "New Conversation"
    };

    setProjects((currentProjects) =>
      currentProjects.map((project) =>
        project.id === projectId
          ? {
              ...project,
              conversations: [...project.conversations, conversation]
            }
          : project
      )
    );
    setMessagesByConversationId((currentMessages) => ({
      ...currentMessages,
      [conversation.id]: createConversationMessages(
        conversation,
        targetProject?.name ?? "New Project"
      )
    }));
    setActiveWorkItemId(conversation.id);
  }

  function toggleProject(projectId) {
    setCollapsedProjectIds((currentIds) =>
      currentIds.includes(projectId)
        ? currentIds.filter((id) => id !== projectId)
        : [...currentIds, projectId]
    );
  }

  function updateDraft(conversationId, value) {
    setDraftsByConversationId((currentDrafts) => ({
      ...currentDrafts,
      [conversationId]: value
    }));
  }

  function sendMessage(conversationId, content) {
    const trimmedContent = content.trim();

    if (!trimmedContent) {
      return;
    }

    const userMessage = {
      id: `user-${crypto.randomUUID()}`,
      content: trimmedContent,
      role: "user"
    };

    setDraftsByConversationId((currentDrafts) => ({
      ...currentDrafts,
      [conversationId]: ""
    }));

    setMessagesByConversationId((currentMessages) => ({
      ...currentMessages,
      [conversationId]: [...(currentMessages[conversationId] ?? []), userMessage]
    }));

    window.setTimeout(() => {
      const statusMessage = {
        id: `status-${crypto.randomUUID()}`,
        content: "CDA is processing...",
        role: "status"
      };

      setMessagesByConversationId((currentMessages) => ({
        ...currentMessages,
        [conversationId]: [...(currentMessages[conversationId] ?? []), statusMessage]
      }));

      window.setTimeout(() => {
        const assistantMessage = {
          id: `assistant-${crypto.randomUUID()}`,
          content: `You said:\n\n${trimmedContent}`,
          role: "assistant"
        };

        setMessagesByConversationId((currentMessages) => ({
          ...currentMessages,
          [conversationId]: [
            ...(currentMessages[conversationId] ?? []).filter(
              (message) => message.id !== statusMessage.id
            ),
            assistantMessage
          ]
        }));
      }, 1000);
    }, 1000);
  }

  return (
    <div className="app-shell">
      <header className="top-menu">
        <div className="brand">{"\u2728 CDA 0.0.1"}</div>
        <nav className="primary-nav" aria-label="Primary">
          <button
            className={activePage === "work" ? "nav-button active" : "nav-button"}
            type="button"
            onClick={() => setActivePage("work")}
          >
            Work
          </button>
          <button
            className={activePage === "settings" ? "nav-button active" : "nav-button"}
            type="button"
            onClick={() => setActivePage("settings")}
          >
            Settings
          </button>
          <a
            className="nav-button"
            href="https://www.corporatedroneagent.ai"
            target="_blank"
            rel="noreferrer"
          >
            Support
          </a>
        </nav>
      </header>

      <div className="workspace">
        <aside className="side-menu" aria-label={`${page.title} menu`}>
          {activePage === "work" ? (
            <div className="side-menu-title action-row">
              <span>{page.title}</span>
              <div className="row-actions">
                <AddButton label="Add project" onClick={addProject} />
              </div>
            </div>
          ) : (
            <div className="side-menu-title">{page.title}</div>
          )}
          {activePage === "work" ? (
            <WorkMenu
              activeItemId={activeWorkItemId}
              collapsedProjectIds={collapsedProjectIds}
              onAddConversation={addConversation}
              onSelect={setActiveWorkItemId}
              onToggleProject={toggleProject}
              projects={projects}
            />
          ) : (
            <nav className="side-menu-items">
              {page.menu.map((item) => (
                <button
                  className={item === activeMenuItem ? "side-button active" : "side-button"}
                  key={item}
                  type="button"
                  onClick={() => setActiveSettingsItem(item)}
                >
                  {item}
                </button>
              ))}
            </nav>
          )}
        </aside>

        <main className="main-body">
          <section className="page-heading" aria-labelledby="page-title">
            <p>{pageContext}</p>
            <h1 id="page-title">{activeMenuItem}</h1>
          </section>

          {activePage === "work" ? (
            <WorkContent
              activeItem={activeWorkItem}
              draftsByConversationId={draftsByConversationId}
              messagesByConversationId={messagesByConversationId}
              onDraftChange={updateDraft}
              onSend={sendMessage}
            />
          ) : (
            <SettingsPanel />
          )}
        </main>
      </div>
    </div>
  );
}

function findWorkItem(projects, activeId) {
  for (const project of projects) {
    if (project.id === activeId) {
      return {
        item: project,
        name: project.name,
        type: "project"
      };
    }

    const conversation = project.conversations.find((item) => item.id === activeId);
    if (conversation) {
      return {
        item: conversation,
        name: conversation.name,
        project,
        type: "conversation"
      };
    }
  }

  return {
    item: projects[0],
    name: projects[0]?.name ?? "Work",
    type: "project"
  };
}

function createInitialMessages(projects) {
  return projects.reduce((messages, project) => {
    project.conversations.forEach((conversation) => {
      messages[conversation.id] = createConversationMessages(
        conversation,
        project.name
      );
    });

    return messages;
  }, {});
}

function createConversationMessages(conversation, projectName) {
  return [
    {
      id: `assistant-seed-${conversation.id}`,
      content: `Ready for **${conversation.name}** in ${projectName}.`,
      role: "assistant"
    }
  ];
}

function WorkMenu({
  activeItemId,
  collapsedProjectIds,
  onAddConversation,
  onSelect,
  onToggleProject,
  projects
}) {
  return (
    <nav className="project-menu" aria-label="Projects and conversations">
      {projects.map((project) => {
        const isCollapsed = collapsedProjectIds.includes(project.id);

        return (
        <section className="project-group" key={project.id}>
          <div className={project.id === activeItemId ? "project-row active" : "project-row"}>
            <button
              className="collapse-button"
              type="button"
              aria-label={`${isCollapsed ? "Expand" : "Collapse"} ${project.name}`}
              aria-expanded={!isCollapsed}
              onClick={() => onToggleProject(project.id)}
            >
              {isCollapsed ? "\u23f5" : "\u23f7"}
            </button>
            <button
              className="project-button"
              type="button"
              onClick={() => onSelect(project.id)}
            >
              {project.name}
            </button>
            <div className="row-actions">
              <OverflowButton label={`${project.name} actions`} />
              <AddButton
                label={`Add conversation to ${project.name}`}
                onClick={() => onAddConversation(project.id)}
              />
            </div>
          </div>
          {!isCollapsed && (
            <div className="conversation-list">
              {project.conversations.map((conversation) => (
                <div
                  className={
                    conversation.id === activeItemId
                      ? "conversation-row active"
                      : "conversation-row"
                  }
                  key={conversation.id}
                >
                  <button
                    className="conversation-button"
                    type="button"
                    onClick={() => onSelect(conversation.id)}
                  >
                    {conversation.name}
                  </button>
                  <OverflowButton label={`${conversation.name} actions`} />
                </div>
              ))}
            </div>
          )}
        </section>
      );
      })}
    </nav>
  );
}

function OverflowButton({ label }) {
  return (
    <button className="overflow-button" type="button" aria-label={label}>
      {"\u2026"}
    </button>
  );
}

function AddButton({ label, onClick }) {
  return (
    <button className="icon-button" type="button" aria-label={label} onClick={onClick}>
      +
    </button>
  );
}

function WorkContent({
  activeItem,
  draftsByConversationId,
  messagesByConversationId,
  onDraftChange,
  onSend
}) {
  if (activeItem.type === "project") {
    return <ProjectSettingsPanel key={activeItem.item.id} project={activeItem.item} />;
  }

  return (
    <ConversationPanel
      conversation={activeItem.item}
      key={activeItem.item.id}
      messages={messagesByConversationId[activeItem.item.id] ?? []}
      onDraftChange={(value) => onDraftChange(activeItem.item.id, value)}
      onSend={(content) => onSend(activeItem.item.id, content)}
      project={activeItem.project}
      value={draftsByConversationId[activeItem.item.id] ?? ""}
    />
  );
}

function ProjectSettingsPanel({ project }) {
  return (
    <section className="settings-panel" aria-label={`${project.name} settings`}>
      <label>
        Project name
        <input type="text" defaultValue={project.name} />
      </label>
      <label>
        Working folder
        <input type="text" placeholder="Optional local folder path" />
      </label>
      <label className="toggle-row">
        <span>Index project sources</span>
        <input type="checkbox" defaultChecked />
      </label>
    </section>
  );
}

function ConversationPanel({ conversation, messages, onDraftChange, onSend, project, value }) {
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
              {message.role === "user" ? "You" : "CDA"}
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

function SettingsPanel() {
  return (
    <section className="settings-panel" aria-label="Settings">
      <label>
        Workspace name
        <input type="text" defaultValue="Corporate Drone Agent" />
      </label>
      <label>
        Default model provider
        <select defaultValue="local">
          <option value="local">Local</option>
          <option value="openai">OpenAI</option>
          <option value="azure">Azure OpenAI</option>
        </select>
      </label>
      <label className="toggle-row">
        <span>Local-first indexing</span>
        <input type="checkbox" defaultChecked />
      </label>
    </section>
  );
}
