# Corporate Drone Agent

An AI agent for corporate drones like me.

Corporate Drone Agent is a local-first personal AI assistant for corporate workers. The idea is simple: download the app, configure an LLM, connect the work systems you already have access to, and run the assistant on your own machine.

Corporate work is spread across project documents, Jira tickets, Confluence pages, SharePoint files, OneDrive documents, GitHub repositories, ServiceNow tickets, Salesforce data, local files, notes, logs, and settings. An assistant is only useful if it can work with that context without requiring a company-wide platform, a large rollout, or a long approval process.

Corporate Drone Agent is meant to be a personal enterprise context agent. It connects through standard APIs, uses the user's existing permissions, and stays practical enough for one person to set up and use. The focus is not another general AI workspace. The focus is helping a corporate worker use the systems, documents, tickets, repositories, and notes they already work with every day.

The application runs as a local Spring Boot backend with a React frontend at [http://localhost:8080](http://localhost:8080). The UI is organized around projects and conversations. A project represents a workspace. Each conversation keeps its own history and context, so separate work streams stay separate.

On startup, the app opens its home page in a Playwright-managed browser window. By default this uses the installed Microsoft Edge browser on Windows, skips Playwright's bundled browser downloads, and opens the window at 90% of the primary screen size centered on screen. Closing that browser window terminates the local app process. For headless or server-style runs, disable this with `--cda.browser.enabled=false`.

The longer-term goal is a local knowledge base that can index and synchronize work context from local folders and corporate systems such as Jira, Confluence, GitHub, SharePoint, OneDrive, ServiceNow, and Salesforce. With that context available locally, the assistant should be able to answer questions, summarize work, draft updates, identify risks, prepare follow-ups, and eventually run scheduled jobs in the relevant conversation.

Write-back to external systems is planned for a later stage. The first priority is safe local reading, indexing, retrieval, summarization, and task support.

The project uses Java 21, Spring Boot, Spring AI, React, Vite, and Maven. LLM configuration is handled in the app settings, with support being developed for OpenAI, the OpenAI official SDK, Azure OpenAI, Ollama, Mistral AI, Google GenAI, and Anthropic Claude. Local application data is stored under `data/`.

API keys are not written to `data/application-settings.json` or returned by the settings API. On Windows, keys are stored in `data/secrets.json` after protection with the current user's DPAPI profile. On non-Windows systems, set `CDA_SECRET_KEY` to enable AES-GCM protection for local secrets.

## Development

Run the full application with Maven:

```powershell
mvn spring-boot:run
```

The app starts on `http://localhost:8080`, opens Microsoft Edge automatically, and exits when the browser window is closed.

To run the backend without opening a browser:

```powershell
mvn spring-boot:run -Dspring-boot.run.arguments="--cda.browser.enabled=false"
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

Run the packaged jar:

```powershell
java -jar target/corporate-drone-agent-0.0.1-SNAPSHOT.jar
```

That jar includes the built React frontend and opens the same Playwright-managed Edge window by default. For server-style jar runs, disable the browser shell:

```powershell
java -jar target/corporate-drone-agent-0.0.1-SNAPSHOT.jar --cda.browser.enabled=false
```

This project is early. The current codebase establishes the local application shell, the project and conversation model, settings, and the basic LLM chat path. The next work is connector support, local indexing, retrieval, scheduled jobs, and packaging for non-technical corporate users.
