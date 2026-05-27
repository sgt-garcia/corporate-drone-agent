# Corporate Drone Agent

Corporate Drone Agent is a local-first personal AI assistant for corporate workers.

Most office employees do not need another chatbot. They need an assistant that understands the context of their real work: project documents, Jira tickets, Confluence pages, SharePoint files, OneDrive documents, GitHub repositories, ServiceNow tickets, Salesforce data, local files, notes, logs, and settings. Today that context is scattered across many tools, while enterprise AI solutions are often too centralized, too expensive, too heavy to roll out, or blocked by governance.

Corporate Drone Agent is meant to be the simpler alternative: download it, configure an LLM, connect the tools you already have access to, and run a personal agent on your own machine.

## Positioning

Corporate Drone Agent is a personal enterprise context agent.

It is not:

- an enterprise-wide AI platform
- a generic chat interface
- an enterprise search replacement
- a marketplace app that requires central approval
- a technical framework that needs complex infrastructure

The goal is practical and deliberately small in scope: help one corporate worker work better with the systems they already use every day.

Corporate Drone Agent should connect through standard APIs, use the user's existing permissions, and avoid requiring Rovo, marketplace apps, enterprise-wide rollout, custom CORS setups, authentication layers, or heavy administration. Compared with tools like AnythingLLM, Open WebUI, LibreChat, OpenClaw, or enterprise search platforms, the focus is less on being a general AI workspace and more on being a personal assistant grounded in corporate work context.

## What It Does

The application runs locally and exposes a React interface at [http://localhost:8080](http://localhost:8080), served by a Spring Boot backend.

The main UI is organized around projects. A project represents a workspace, and each project contains conversations. A conversation has its own history and context, making it possible to keep work streams separate instead of mixing everything into one long chat.

The current application includes:

- local Spring Boot backend bound to `127.0.0.1`
- React frontend built with Vite
- project and conversation management
- local JSON-backed persistence under `data/`
- chat settings for OpenAI, OpenAI official SDK, Azure OpenAI, and Ollama
- conversation memory through Spring AI
- server-sent events for live message updates
- an echo mode when no model is configured

## Product Direction

The longer-term goal is a local knowledge base that can index and synchronize work context from sources such as:

- local folders
- Jira projects
- Confluence spaces
- GitHub repositories
- SharePoint sites
- OneDrive documents
- ServiceNow tickets
- Salesforce records
- notes, logs, and configuration files

The intended flow is:

1. Configure the LLM provider you are allowed to use.
2. Configure the work sources you already have access to.
3. Let the agent build a local searchable context layer.
4. Ask questions, summarize work, draft updates, identify risks, and prepare follow-ups from that context.
5. Optionally schedule recurring jobs inside conversations.

Write-back to external systems is planned for a later stage. The first priority is safe local reading, indexing, summarization, and task support.

## Architecture

Corporate Drone Agent is currently scaffolded as a Spring Boot application with a packaged React frontend.

- Backend: Java 21, Spring Boot, Spring AI
- Frontend: React, Vite
- Build: Maven with `frontend-maven-plugin`
- Storage: local JSON files under `data/`
- LLM providers: OpenAI, OpenAI official SDK, Azure OpenAI, Ollama

The Maven build installs local Node/npm tooling, builds the frontend from `frontend/`, and packages the generated assets into the Spring Boot application.

## Development

Run the full application:

```powershell
mvn spring-boot:run
```

Then open:

```text
http://localhost:8080
```

Run the frontend only:

```powershell
cd frontend
npm install
npm run dev
```

Build the packaged application:

```powershell
mvn clean package
```

## Status

This project is early and intentionally focused. The current codebase establishes the local app shell, project/conversation model, settings, and LLM chat path. The major upcoming work is connector support, local indexing, retrieval, scheduled jobs, and stronger packaging for non-technical corporate users.
