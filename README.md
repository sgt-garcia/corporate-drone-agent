# Corporate Drone Agent

An AI agent for corporate drones like me.

Corporate Drone Agent is a local-first personal AI assistant for corporate workers. The idea is simple: download the app, configure an LLM, connect the work systems you already have access to, and run the assistant on your own machine.

Corporate work is spread across project documents, Jira tickets, Confluence pages, SharePoint files, OneDrive documents, GitHub repositories, ServiceNow tickets, Salesforce data, local files, notes, logs, and settings. An assistant is only useful if it can work with that context without requiring a company-wide platform, a large rollout, or a long approval process.

Corporate Drone Agent is meant to be a personal enterprise context agent. It connects through standard APIs, uses the user's existing permissions, and stays practical enough for one person to set up and use. The focus is not another general AI workspace. The focus is helping a corporate worker use the systems, documents, tickets, repositories, and notes they already work with every day.

The application runs as a local Spring Boot backend with a React frontend at [http://localhost:8080](http://localhost:8080). The UI is organized around projects and conversations. A project represents a workspace. Each conversation keeps its own history and context, so separate work streams stay separate.

The longer-term goal is a local knowledge base that can index and synchronize work context from local folders and corporate systems such as Jira, Confluence, GitHub, SharePoint, OneDrive, ServiceNow, and Salesforce. With that context available locally, the assistant should be able to answer questions, summarize work, draft updates, identify risks, prepare follow-ups, and eventually run scheduled jobs in the relevant conversation.

Write-back to external systems is planned for a later stage. The first priority is safe local reading, indexing, retrieval, summarization, and task support.

The project uses Java 21, Spring Boot, Spring AI, React, Vite, and Maven. LLM configuration is handled in the app settings, with support being developed for OpenAI, the OpenAI official SDK, Azure OpenAI, and Ollama. Local application data is stored under `data/`.

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

This project is early. The current codebase establishes the local application shell, the project and conversation model, settings, and the basic LLM chat path. The next work is connector support, local indexing, retrieval, scheduled jobs, and packaging for non-technical corporate users.
