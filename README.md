# Corporate Drone's Agent

Corporate Drone's Agent is a local-first personal AI assistant for corporate work.
It runs on your machine, stores its data locally, and lets you configure the LLM
provider you want to use for chat.

The application is currently an early desktop-style local web app: a Spring Boot
backend serves a React/Vite frontend, opens the UI in a Playwright-managed
browser window, and keeps projects, conversations, settings, and secrets on the
local machine.

## Current status

Working today:

- Local Spring Boot application served at `http://localhost:8080`.
- React UI with two main areas: Work and Settings.
- Project creation and editing, including a project name, optional working
  folder, and project-level custom instructions.
- Conversation creation inside projects.
- Persistent projects, conversations, assistant message history, durable
  settings, knowledge-folder configuration, and scan metadata stored in the
  local encrypted H2 database.
- Async assistant replies with transient status/error updates pushed to the UI
  over server-sent events.
- Configurable global assistant name, active AI provider, and custom
  instructions.
- LLM chat support for OpenAI, OpenAI Official SDK, Azure OpenAI, Ollama,
  Mistral AI, Google Gemini, Anthropic, Groq, and DeepSeek.
- Provider model/deployment lookup from the Settings screen, using saved API
  keys when available.
- Local API-key storage that avoids returning secrets from the settings API.
- Settings -> Knowledge -> Local Folders backend support for adding, removing,
  pausing, resuming, and scanning local folders.
- Backend validation for local folders, including existence checks, duplicate
  prevention, a 10-folder limit, and prevention of nested folder roots.
- Scheduled local-folder scans every 15 minutes, at minute 0, 15, 30, and 45.
- Recursive local-folder scanning into an encrypted H2 knowledge database.
- Knowledge-folder scan status and counters are stored separately from durable
  provider/user settings, so scan progress does not rewrite the settings
  document.
- Local read, conversion, character chunking, and Lucene indexing for common
  text formats, with files larger than 1 MB skipped for now.
- Best-effort local knowledge retrieval for chat prompts using indexed chunks,
  with retrieved snippets added as untrusted context instead of system
  instructions.
- Operational logging for local-folder configuration, scans, indexing, cleanup,
  and retrieval failures.
- Automatic browser launch on startup, with app shutdown when the browser closes
  by default.

Still planned:

- Connectors for corporate systems such as Jira, Confluence, GitHub,
  SharePoint, OneDrive, ServiceNow, and Salesforce.
- Indexing and retrieval for non-local-folder sources such as repositories,
  tickets, and documents.
- Rich document conversion beyond the current common text formats.
- Scheduled jobs tied to conversations and projects.
- Write-back workflows for external systems.
- End-user packaging beyond the current runnable jar.

## Technology

- Java 21
- Spring Boot 3.5.14
- Spring AI 1.1.2
- H2 for the local encrypted application and knowledge database, accessed
  through HikariCP
- Flyway for database schema setup
- Apache Lucene for local full-text indexing
- Maven
- Node.js 22.16.0 and npm 10.9.2 for the frontend build
- React 19
- Vite 8
- Playwright for the local browser shell

The Maven build installs the configured Node.js and npm versions, builds the
frontend, copies `frontend/dist` into the Spring Boot static resources, and then
packages the backend.

## Backend structure

The backend is organized around a few focused seams:

- `SettingsController` owns the main settings read/write API.
- `KnowledgeFolderController` owns local knowledge-folder management and scan
  actions.
- `ProviderModelsController` owns provider model/deployment lookup endpoints.
- `AiChatService` selects a `ChatProvider` by provider id and delegates
  provider-specific validation, model construction, and reply generation.
- Provider model lookup services share common request, saved-key resolution,
  exception handling, filtering, de-duplication, and sorting through
  `ModelLookupSupport`.
- API-key-backed provider settings share `ApiKeySettings` /
  `ApiKeyModelSettings`, and `SettingsSecretsService` drives migration, save,
  status, apply, and clear behavior from a descriptor list.
- `KnowledgeDatabaseConfig` creates the encrypted H2 datasource as a HikariCP
  pool, preserving the custom cipher/key setup while keeping normal pooled JDBC
  behavior.
- `SettingsRepository`, `ProjectRepository`, `ConversationRepository`, and
  `KnowledgeRootRepository` store application state in H2 through JDBC.
  Settings are stored as a single non-secret JSON document in `app_settings`;
  local knowledge-folder configuration and volatile scan status live in
  `knowledge_roots`; projects, conversations, and messages use relational
  tables. Message sends append one row to `conversation_messages`, while
  project and conversation list views use lightweight summary queries instead
  of loading full message history. Deleting a project relies on database
  cascades to remove its conversations and messages.
- `MessagePushJob` persists only genuine assistant replies. Status updates,
  provider validation failures, request failures, and timeouts are published as
  transient `status` / `error` messages and are not replayed into future prompt
  history.
- `EventService` fans out SSE events on a bounded background executor so slow
  clients do not block scan, settings, or request threads. Broad settings and
  project changes are sent as lightweight invalidation events that the frontend
  uses to refetch current state.
- `KnowledgeResourcePipelineRepository` keeps read, conversion, and index saves
  on a shared table-binding helper so insert/update timestamp behavior stays
  consistent across pipeline stages.

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

## Knowledge

Local folders are configured in Settings under **Knowledge -> Local Folders**.
The backend currently supports up to 10 local folder roots. A folder must exist,
must not already be configured, and must not contain or be contained by another
configured root.

Each enabled local folder can be scanned manually from the settings screen and
is also scanned by a scheduled job every 15 minutes, exactly at minute 0, 15,
30, and 45. Scans are recursive. Paused folders remain configured but are
skipped by the scheduled scan. Removing a folder also removes its knowledge root
from the database and deletes its Lucene documents. If the folder is being
scanned when it is removed, the current scan is asked to stop before the next
file and removal waits for that scan to finish.

For local folders, the indexing pipeline is:

1. Scan the root and record resources.
2. Read supported files.
3. Convert readable content to markdown/text.
4. Split converted content into character chunks.
5. Index each chunk in Lucene.

The first implementation intentionally keeps conversion narrow: common text
formats are supported, files larger than 1 MB are skipped, and richer document
conversion is planned separately. Chat requests perform a best-effort Lucene
search over indexed chunks. Matching snippets are added to the model prompt as a
separate user context message and are explicitly marked as untrusted reference
content, not instructions. If retrieval fails, chat continues without local
knowledge context and the failure is logged.

Scan progress, last result, resource counts, and pause state are stored on the
knowledge-root rows in the database instead of in the durable settings document.
This keeps scheduled scans from rewriting provider configuration while they
update runtime status.

## Local data

By default, application data is written under your user profile in
`.corporate-drone-agent`. On Windows this is typically
`C:\Users\your-user\.corporate-drone-agent`:

- `.corporate-drone-agent/database/knowledge*` stores the encrypted H2 database,
  including durable application settings, projects, conversations, assistant
  message history, knowledge-folder configuration, and knowledge metadata.
- `.corporate-drone-agent/secrets.json` stores protected API keys.
- `.corporate-drone-agent/lucene/` stores the Lucene full-text index.

The database schema is managed with Flyway migrations in
`src/main/resources/db/migration`. Application state is intentionally database
backed: settings, knowledge folders, projects, conversations, and messages are
no longer stored as local JSON files. Project, conversation, and message rows
use relational foreign keys; deleting a project cascades through its
conversations and messages in the database.

The application logs local knowledge lifecycle events such as folder
add/remove/pause/resume, scheduled scans, scan completion/failure/cancellation,
index cleanup, read failures, indexing failures, and retrieval failures. Expected
per-file skips, such as unsupported formats and files over 1 MB, are logged at
debug level.

The storage root can be changed with:

```powershell
--cda.storage.root=path\to\data
```

API keys are accepted through the settings UI/API, then moved into the secret
store. They are not written to the database-backed application settings document
and are not returned by `GET /api/settings`.

Only durable assistant replies are persisted as assistant messages. In-flight
status, provider validation errors, request failures, and timeouts are delivered
to the current UI session as transient message roles and are not stored as model
output.

Secret protection:

- On Windows, secrets are protected with the current user's DPAPI profile.
- On non-Windows systems, set `CDA_SECRET_KEY` to enable AES-GCM protection.

The H2 database is encrypted at rest. Its encryption key is generated
automatically and stored in the same protected local secret store as provider
API keys, so a copied database is not useful without access to that machine's
protected secret material.

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
- `GET /api/settings/knowledge/local-folders`
- `POST /api/settings/knowledge/local-folders`
- `DELETE /api/settings/knowledge/local-folders/{folderId}`
- `POST /api/settings/knowledge/local-folders/{folderId}/scan`
- `POST /api/settings/knowledge/local-folders/{folderId}/pause`
- `POST /api/settings/knowledge/local-folders/{folderId}/resume`
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
(including creation and deletion), message, and settings updates. Event delivery
is asynchronous on the backend. Settings and project update events are
lightweight invalidations; the frontend refetches the current settings/projects
after receiving them. The settings model/deployment endpoints are used by the
frontend to populate provider model selectors.

## Tests

Run the test suite with:

```powershell
mvn test
```

The current tests cover Spring context startup, browser/headless mode selection,
prompt construction, filtering transient error/status messages out of prompt
history, local knowledge prompt context and retrieval failure logging, API-key
serialization/migration behavior, settings validation, provider model/deployment
lookup parsing, local-folder scan/read/convert/chunk/index behavior, scan
cancellation during folder removal, database-backed application settings,
database-backed knowledge folders, project/conversation deletion and ordering,
append-only message storage, conversation summary queries, SSE fan-out behavior,
and knowledge database repositories.
