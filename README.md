# Corporate Drone's Agent

Corporate Drone's Agent is a local-first personal AI assistant for corporate work.
It runs on your machine, stores its data locally, and lets you configure the LLM
provider you want to use for chat.

The application is currently an early desktop-style local web app: a Spring Boot
backend serves a React/Vite frontend, opens the UI in a Playwright-managed
browser window, and keeps projects, conversations, settings, and secrets on the
local filesystem.

## Current status

Working today:

- Local Spring Boot application served at `http://localhost:8080`.
- React UI with two main areas: Work and Settings.
- Project creation and editing, including a project name, optional working
  folder, and project-level custom instructions.
- Conversation creation inside projects.
- Persistent conversation history stored as local JSON files.
- Async assistant replies with status updates pushed to the UI over server-sent
  events.
- Configurable global assistant name, active AI provider, and custom
  instructions.
- LLM chat support for OpenAI, OpenAI Official SDK, Azure OpenAI, Ollama,
  Mistral AI, Google Gemini, Anthropic, Groq, and DeepSeek.
- Provider model/deployment lookup from the Settings screen, using saved API
  keys when available.
- Local API-key storage that avoids returning secrets from the settings API.
- Automatic browser launch on startup, with app shutdown when the browser closes
  by default.

Still planned:

- Connectors for corporate systems such as Jira, Confluence, GitHub,
  SharePoint, OneDrive, ServiceNow, and Salesforce.
- Local indexing and retrieval over files, repositories, tickets, and documents.
- Scheduled jobs tied to conversations and projects.
- Write-back workflows for external systems.
- End-user packaging beyond the current runnable jar.

## Technology

- Java 21
- Spring Boot 3.5.14
- Spring AI 1.1.2
- Maven
- Node.js 22.16.0 and npm 10.9.2 for the frontend build
- React 19
- Vite 8
- Playwright for the local browser shell

The Maven build installs the configured Node.js and npm versions, builds the
frontend, copies `frontend/dist` into the Spring Boot static resources, and then
packages the backend.

## AI providers

Chat providers are configured in Settings under **Models & providers**, which
lists each provider as a connection-status card you open to enter its key (or
endpoint/base URL) and pick a model. The default provider for new conversations
is chosen in **General**.

| Provider | Required settings | Model lookup |
| --- | --- | --- |
| OpenAI | API key, model | Lists OpenAI chat models |
| OpenAI (SDK) | API key, model | Lists OpenAI chat models |
| Azure OpenAI | endpoint, API key, deployment name | Lists compatible deployments |
| Ollama | base URL, model | Lists local Ollama chat models |
| Mistral | API key, model | Lists active Mistral chat models |
| Gemini | API key, model | Lists Gemini generation models |
| Anthropic | API key, model | Lists Anthropic models |
| Groq | API key, model | Lists Groq chat models |
| DeepSeek | API key, model | Lists DeepSeek chat models |

When no provider is configured, the app falls back to a local echo response so
the conversation flow can still be exercised without an external API key.

## Local data

By default, application data is written under your user profile in
`.corporate-drone-agent`. On Windows this is typically
`C:\Users\your-user\.corporate-drone-agent`:

- `.corporate-drone-agent/application-settings.json` stores non-secret
  settings.
- `.corporate-drone-agent/projects/*.json` stores projects.
- `.corporate-drone-agent/conversations/*.json` stores conversations and
  message history.
- `.corporate-drone-agent/secrets.json` stores protected API keys.

The storage root can be changed with:

```powershell
--cda.storage.root=path\to\data
```

API keys are accepted through the settings UI/API, then moved into the secret
store. They are not written to `application-settings.json` and are not returned
by `GET /api/settings`.

Secret protection:

- On Windows, secrets are protected with the current user's DPAPI profile.
- On non-Windows systems, set `CDA_SECRET_KEY` to enable AES-GCM protection.

## Running the app

Run the full application with Maven:

```powershell
mvn spring-boot:run
```

The app starts on `http://localhost:8080`, opens Microsoft Edge by default, and
exits when the browser window is closed.

Run the backend without opening the browser shell:

```powershell
mvn spring-boot:run -Dspring-boot.run.arguments="--cda.browser.enabled=false"
```

Useful browser options:

```powershell
--cda.browser.enabled=false
--cda.browser.channel=msedge
--cda.browser.headless=false
--cda.browser.terminate-on-close=true
--cda.browser.window-scale=0.9
--cda.browser.url=http://localhost:8080/
```

## Frontend development

For frontend-only development, run the backend in server mode first, then start
Vite:

```powershell
mvn spring-boot:run -Dspring-boot.run.arguments="--cda.browser.enabled=false"
cd frontend
npm install
npm run dev
```

The Vite dev server proxies `/api` requests to `http://localhost:8080`.

## Build and package

Build the packaged application:

```powershell
mvn clean package
```

Run the packaged jar:

```powershell
java -jar target/corporate-drone-agent-0.0.1-SNAPSHOT.jar
```

Run the jar without opening the browser shell:

```powershell
java -jar target/corporate-drone-agent-0.0.1-SNAPSHOT.jar --cda.browser.enabled=false
```

## API surface

The current backend exposes:

- `GET /api/projects`
- `POST /api/projects`
- `PUT /api/projects/{projectId}`
- `GET /api/projects/{projectId}/conversations`
- `POST /api/projects/{projectId}/conversations`
- `DELETE /api/projects/{projectId}`
- `GET /api/conversations/{conversationId}`
- `PUT /api/conversations/{conversationId}`
- `DELETE /api/conversations/{conversationId}`
- `POST /api/conversations/{conversationId}/messages`
- `GET /api/settings`
- `PUT /api/settings`
- `POST /api/settings/openai-models`
- `POST /api/settings/azure-openai-deployments`
- `POST /api/settings/ollama-models`
- `POST /api/settings/mistral-models`
- `POST /api/settings/gemini-models`
- `POST /api/settings/anthropic-models`
- `POST /api/settings/groq-models`
- `POST /api/settings/deepseek-models`
- `GET /api/events`

`/api/events` is an SSE stream used by the frontend for project, conversation
(including creation and deletion), message, and settings updates. The settings
model/deployment endpoints are used by the frontend to populate provider model
selectors.

## Tests

Run the test suite with:

```powershell
mvn test
```

The current tests cover Spring context startup, browser/headless mode selection,
prompt construction, API-key serialization/migration behavior, and provider
model/deployment lookup parsing.
