# Corporate Drone Agent

An AI agent for corporate drones like me.

The Corporate Drone's Agent is a local-first personal AI assistant for corporate workers. It is built around a simple idea: a corporate employee should be able to download an app, configure an LLM, connect the work systems they already have access to, and run a useful assistant on their own machine.

Real work is spread across project documents, Jira tickets, Confluence pages, SharePoint files, OneDrive documents, GitHub repositories, ServiceNow tickets, Salesforce data, local files, notes, logs, and settings. A useful assistant needs to understand that working environment without requiring a company-wide AI platform, a heavy rollout, or a long chain of approvals.

The Corporate Drone's Agent is meant to be a personal enterprise context agent. It should connect through standard APIs, use the user's existing permissions, and stay practical enough for one worker to set up and use. The focus is not on becoming another general AI workspace. The focus is helping a corporate worker reason over the systems, documents, tickets, repositories, and notes they already touch every day.

The application currently runs as a local Spring Boot backend with a React frontend served at [http://localhost:8080](http://localhost:8080). The main UI is organized around projects and conversations. A project represents a workspace, and each conversation keeps its own history and context, so different work streams do not collapse into one long chat.

The longer-term goal is a local knowledge base that can index and synchronize work context from local folders and corporate systems such as Jira, Confluence, GitHub, SharePoint, OneDrive, ServiceNow, and Salesforce. Once that context is available locally, the assistant should be able to answer questions, summarize work, draft updates, identify risks, prepare follow-ups, and eventually run scheduled jobs inside the relevant conversation.

Write-back to external systems is planned for a later stage. The first priority is safe local reading, indexing, retrieval, summarization, and task support.

The project is built with Java 21, Spring Boot, Spring AI, React, Vite, and Maven. LLM configuration is handled through the app settings, with support being developed around OpenAI, the OpenAI official SDK, Azure OpenAI, and Ollama. Local application data is stored under `data/`.

## Development

Run the full application with Maven:

```powershell
mvn spring-boot:run
```

Then open:

```text
http://localhost:8080
```

For frontend-only development:

```powershell
cd frontend
npm install
npm run dev
```

Build the packaged application:

```powershell
mvn clean package
```

This project is early. The current codebase establishes the local application shell, the project and conversation model, settings, and the basic LLM chat path. The next substantial work is connector support, local indexing, retrieval, scheduled jobs, and packaging that feels approachable for non-technical corporate users.
