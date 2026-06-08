# Corporate Drone's Agent

Corporate Drone's Agent is a local-first personal work agent for corporate
knowledge workers.

It is an open-source personal project, not an internal company platform. It
runs on your machine, stores its application data locally, lets you choose your
chat provider, and can index local folders so conversations can use your own
files as reference context.

The project is currently an early desktop-style web app: a Spring Boot backend
serves a React/Vite frontend, opens the UI in a Playwright-managed browser
window, and shuts down with that browser by default.

## Vision

Corporate Drone's Agent is a practical step toward the idea of **Identic AI**:
an AI that becomes a persistent, personalized, work-aware extension of a
specific person.

The destination is not just "an AI that answers questions." The long-term idea
is closer to a digital chief of staff for work: an agent that understands your
projects, documents, tickets, repositories, conversations, settings, routines,
preferences, and long-running goals well enough to help you navigate corporate
work with continuity.

CDA starts with a narrower and more realistic first promise:

```text
Know what I know at work.
```

That means connecting the assistant to user-owned context first: local folders
today, then corporate systems such as Jira, Confluence, GitHub, SharePoint,
OneDrive, ServiceNow, and Salesforce.

## Positioning

CDA is not trying to be a generic enterprise search platform or a company-wide
chatbot. It is closer to an Identic AI for knowledge workers:

```text
my agent, my folders, my projects, my conversations, my tools, my local
knowledge, my routines.
```

That makes the project different from enterprise-wide assistants such as
Microsoft Copilot, Glean, Moveworks, Gemini Enterprise, or Sinequa. Those tools
are usually deployed at the organization level. CDA is designed as a personal,
self-controlled work operating system for the individual corporate worker.

The guiding principles are:

- Local-first by default.
- User-owned context and secrets.
- Simple setup for a single person.
- API-first connectors to real work systems.
- Persistent memory and goal management over time.
- Controlled autonomous action, especially for scheduled or background work.

## What Works

- Projects and conversations, with persisted message history.
- Project-level working folders and custom instructions.
- Spring AI filesystem tools for project working folders, exposed to the
  assistant as `/`.
- Global assistant name, provider choice, and custom instructions.
- Async assistant replies with live status/error events over SSE.
- Local knowledge folders with add, remove, pause, resume, manual scan, and
  scheduled scan support.
- Recursive local-folder indexing into Lucene-backed chunks.
- Best-effort local knowledge retrieval added to chat prompts as untrusted
  reference context.
- Local encrypted H2 storage for settings, projects, conversations, messages,
  knowledge roots, scan status, and knowledge metadata.
- Protected local API-key storage that keeps secrets out of settings responses.
- Automatic browser launch on startup.

Supported chat providers:

- OpenAI
- OpenAI official SDK
- Azure OpenAI
- Amazon Bedrock
- Ollama
- Mistral AI
- Google Gemini
- Anthropic
- Groq
- DeepSeek

If no provider is configured, the app uses a local echo response so the
conversation flow still works.

## Still Coming

- Corporate connectors such as Jira, Confluence, GitHub, SharePoint, OneDrive,
  ServiceNow, and Salesforce.
- Indexing and retrieval for repositories, tickets, cloud documents, and other
  non-local sources.
- Richer document conversion beyond the current text-oriented pipeline.
- Scheduled jobs tied to projects and conversations.
- Persistent personal memory, preferences, and long-term goal tracking.
- Write-back workflows for external systems.
- End-user packaging beyond the runnable jar.

## Run

Start the app:

```powershell
mvn spring-boot:run
```

The app starts at `http://localhost:8080`, opens Microsoft Edge by default, and
exits when the browser window closes.

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

## Frontend Development

Run the backend in server mode, then start Vite:

```powershell
mvn spring-boot:run -Dspring-boot.run.arguments="--cda.browser.enabled=false"
cd frontend
npm install
npm run dev
```

Vite proxies `/api` requests to `http://localhost:8080`.

## Build

Build the packaged app:

```powershell
mvn clean package
```

Run the jar:

```powershell
java -jar target/corporate-drone-agent-0.0.1-SNAPSHOT.jar
```

Run the jar without opening the browser shell:

```powershell
java -jar target/corporate-drone-agent-0.0.1-SNAPSHOT.jar --cda.browser.enabled=false
```

The Maven build installs the configured Node.js/npm versions, builds the
frontend, copies `frontend/dist` into Spring static resources, and packages the
backend.

## Local Data

By default, data is written under your user profile in
`.corporate-drone-agent`. On Windows this is typically:

```text
C:\Users\your-user\.corporate-drone-agent
```

Important paths:

- `database/knowledge*` stores the encrypted H2 database.
- `secrets.json` stores protected API keys and the generated database key.
- `lucene/` stores the full-text knowledge index.

Change the storage root with:

```powershell
--cda.storage.root=path\to\data
```

Secrets are protected with the current user's DPAPI profile on Windows. On
non-Windows systems, set `CDA_SECRET_KEY` to enable AES-GCM protection.

API keys are accepted through the settings UI/API, moved into the local secret
store, and not returned by `GET /api/settings`.

## Knowledge

Local folders are configured in Settings under `Knowledge -> Local Folders`.

Rules:

- Up to 10 local folder roots.
- A folder must exist.
- Duplicate roots are rejected.
- Nested roots are rejected.
- Paused folders remain configured but are skipped by scheduled scans.

Scans run recursively. They can be started manually from Settings and also run
every 15 minutes at minute 0, 15, 30, and 45.

The local-folder pipeline:

1. Scan the root and record resources.
2. Read supported files.
3. Convert readable content to markdown/text.
4. Split content into character chunks.
5. Index chunks in Lucene.

The current converter intentionally stays narrow: common text formats are
supported, files larger than 1 MB are skipped, and richer document conversion is
planned separately.

When a chat starts, matching local snippets are added to the prompt as a
separate context message. They are explicitly treated as untrusted reference
material, not instructions. If retrieval fails, chat continues without local
knowledge context and the failure is logged.

## Project Filesystem Tools

When a project has a working folder, chat providers receive a filesystem tool
set implemented with Spring AI `@Tool` annotations. The assistant sees only the
project working folder, and that folder is presented as the virtual root `/`.
For example, a local file such as:

```text
C:\work\my-project\README.md
```

is addressed by the assistant as:

```text
/README.md
```

Local absolute paths and traversal outside `/` are rejected.

Available tools:

- `read_text_file` and deprecated alias `read_file`
- `read_media_file`
- `read_multiple_files`
- `write_file`
- `edit_file`
- `create_directory`
- `list_directory`
- `list_directory_with_sizes`
- `directory_tree`
- `move_file`
- `search_files`
- `get_file_info`
- `list_allowed_directories`

Safety rules:

- Existing paths are resolved with real-path checks before access.
- Writes reject existing symlink destinations.
- Directory tree traversal does not descend through symlinked directories.
- Directory size reporting uses no-follow metadata so symlinks cannot leak
  target sizes outside the working folder.
- `edit_file` applies exact line-sequence edits and fails when the requested
  old text is missing or ambiguous.

Text handling:

- `read_text_file` supports BOM-marked UTF-8, UTF-16BE, and UTF-16LE, then
  falls back through strict UTF-8, Windows-1252, and ISO-8859-1.
- `edit_file` preserves the detected charset and byte-order mark when writing
  edited content back.
- `write_file` writes new text content as UTF-8.

## Technology

- Java 21
- Spring Boot 3.5.14
- Spring AI 1.1.2
- H2, HikariCP, and Flyway
- Apache Lucene
- Maven
- Node.js 22.16.0 and npm 10.9.2
- React 19
- Vite 8
- Playwright

## Project Map

- `src/main/java/ai/corporatedroneagent/controller` exposes projects,
  conversations, settings, provider model lookup, knowledge folders, and SSE
  events.
- `src/main/java/ai/corporatedroneagent/service` contains chat orchestration,
  provider lookup, settings/secrets behavior, project workflows, and local
  knowledge scanning/retrieval.
- `src/main/java/ai/corporatedroneagent/tools` contains assistant tool
  implementations, including project-scoped filesystem tools.
- `src/main/java/ai/corporatedroneagent/repository` stores settings, projects,
  conversations, messages, knowledge roots, and knowledge pipeline state.
- `src/main/java/ai/corporatedroneagent/security` protects local secrets.
- `src/main/java/ai/corporatedroneagent/packaging` owns browser lifecycle and
  app termination.
- `src/main/resources/db/migration` contains Flyway migrations.
- `frontend/src` contains the React UI.

## API

Main backend areas:

- `/api/projects`
- `/api/projects/{projectId}/conversations`
- `/api/conversations/{conversationId}`
- `/api/conversations/{conversationId}/messages`
- `/api/settings`
- `/api/settings/knowledge/local-folders`
- `/api/settings/*-models`
- `/api/events`

`/api/events` is the server-sent event stream used by the frontend for project,
conversation, message, and settings updates. Broad settings and project events
are lightweight invalidations; the frontend refetches current state after
receiving them.

## Tests

Run tests with:

```powershell
mvn test
```

The suite covers application startup, browser/headless behavior, prompt
construction, transient message filtering, settings validation, provider model
lookup, secret migration/serialization behavior, local knowledge scanning and
retrieval, database-backed settings/projects/conversations/messages, project
deletion cascades, SSE fan-out, repository behavior, and Spring AI filesystem
tool invocation.
