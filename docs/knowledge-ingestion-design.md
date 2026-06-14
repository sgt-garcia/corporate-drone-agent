# Knowledge Ingestion — Design

## 1. Context & goal

A knowledge source (Local Folder, Jira project, and many more to come) flows through three
parts: **configuration** (the UI + endpoints where you add it), **ingestion** (the internal
processing that turns it into searchable knowledge), and **retrieval** (its use in
conversations).

- **Retrieval is already source-agnostic.** Search runs over a Lucene index of resource
  *chunks* and rehydrates text uniformly; the only source-specific code is a Jira issue-key
  relevance boost and a couple of cosmetic labels/icons. Adding a source needs no retrieval
  changes.
- **Ingestion has a shared *core* but not a shared *framework*.** The downstream half (chunk →
  index → persist stages) is genuinely reused; the scan loop, root lifecycle, and orchestration
  are copy-pasted per source. The Jira and Folder implementations are now *isomorphic* (after the
  structural-parity work), so they can be collapsed into one shared shape.

**Goal:** make ingestion a generic reconciliation loop where the only per-source code is "how to
talk to the source." Adding a source becomes: implement one adapter, register it.

## 2. Principle & mental model

**Ingestion is sync, not import.** It keeps the local index in step with an external source.
Every source reduces to the same facts about its **items**:

1. a **stable identity** (a reference),
2. a **change signal** (hash, or size + last-modified, or version),
3. **fetchable content** (bytes/text + a declared format).

If a source can *enumerate its items* and *fetch one item*, it can be ingested. Folders already
do this (walk tree = enumerate, read file = fetch); Jira already does this
(`fetchProjectIssueManifests` = enumerate, `fetchIssueDocument` = fetch).

```
                     ┌──────────────────────────────────────────────┐
   trigger           │            Ingestion Orchestrator            │   generic
 (manual / cron) ───▶│  resolve root · dedupe · status · SSE · errs │
                     └───────────────────┬──────────────────────────┘
                                         │ runs
                     ┌───────────────────▼──────────────────────────┐
                     │               Ingestion Engine               │   generic
                     │  enumerate ▸ diff ▸ fetch ▸ convert ▸ chunk  │
                     │  ▸ index ▸ reconcile-deletes ▸ advance-cursor │
                     └───────┬────────────────────────────┬─────────┘
            per-source ◀─────┘                            └────▶ shared
        ┌───────────────────────┐            ┌───────────────────────────┐
        │     Source Adapter    │            │  Converters (by format)   │
        │  enumerate(cursor)    │            │  Chunker · Indexer/Lucene │
        │  fetch(manifest)      │            │  Pipeline repo · stages   │
        │  isUnchanged(...)     │            └───────────────────────────┘
        └───────────────────────┘
         Folder · Jira · GitHub · Confluence · …
```

Only the **Source Adapter** is written per source. Everything else is shared.

**Decoupling:** content *acquisition* (the source) is independent of content *rendering*
(format → text). A PDF from a folder and a PDF from SharePoint use the same converter; the
adapter just declares `format = "pdf"` and hands over bytes.

## 3. Target architecture

### 3.1 Contracts

```java
// One per source type. Stateless; resolves config/auth into a per-scan session.
interface KnowledgeSourceAdapter {
    KnowledgeSource source();
    // Validate config/connectivity for this root and open a session, or throw
    // ResponseStatusException if the source isn't scannable (e.g. missing setup).
    KnowledgeScanSession openSession(KnowledgeRoot root);
}

// A prepared scan of one root. Holds resolved config/credentials.
interface KnowledgeScanSession extends AutoCloseable {
    // Cheap listing — no content download. `cursor` is the source's incremental hint.
    List<ResourceManifest> enumerate(ScanCursor cursor);
    // Full content for one changed item. Only called for items the engine decides to (re)index.
    ResourceDocument fetch(ResourceManifest manifest);
    // Source-specific change detection against the stored resource (timestamp, size, hash…).
    boolean isUnchanged(KnowledgeResource existing, ResourceManifest manifest);
    // Whether deletions are reconciled from enumeration (full sources: true; cursor/delta: false).
    boolean reconcilesDeletes();
    @Override default void close() {}
}

// Lightweight item metadata for the diff step. `handle` is adapter-private (e.g. a Jira id).
record ResourceManifest(
    String reference, String displayName, String format,
    long sizeBytes, Instant lastModifiedAt, Object handle) {}

// Full content for one item; ready to convert/chunk/index.
record ResourceDocument(
    String reference, String displayName, String format,
    long sizeBytes, Instant lastModifiedAt, byte[] readValue) {}

// Opaque incremental hint persisted in KnowledgeRoot.configJson between scans.
record ScanCursor(Instant lastSuccessfulScanStartedAt) {}   // grows as needed

// Format → text. Registered beans; engine picks by ResourceDocument.format.
interface FormatConverter {
    boolean supports(String format);
    String toText(byte[] readValue);
}
```

`KnowledgeSource` becomes the discriminator that the registry maps to an adapter (plus, later, a
presentation descriptor for retrieval/UI).

### 3.2 The engine algorithm (generic, source-blind)

`KnowledgeScanEngine.scan(KnowledgeRoot root, KnowledgeSourceAdapter adapter, BooleanSupplier
isCancelled, Consumer<String> onProgress) → ScanResult`

1. `startRootScan(root)` → `IN_PROGRESS`, clear success/message, set `scanStartedAt`; `startScan`
   record. (Orchestrator publishes `settings-updated`.)
2. `session = adapter.openSession(root)`; `cursor = ScanCursor.from(root.configJson)`.
3. `manifests = session.enumerate(cursor)`.
4. Load existing resources by reference; load the reusable-pipeline set
   (`findReusablePipelineResourceIdsByRootId`).
5. For each manifest (checking `isCancelled` between items, emitting `onProgress`):
   - If an existing resource is reusable **and** `session.isUnchanged(existing, manifest)` → skip.
   - Else `doc = session.fetch(manifest)`; persist `KnowledgeResource`; `deleteResource` (clear old
     index); record read; `text = converters.toText(doc)`; record conversion;
     `chunks = chunker.chunk(...)`; `indexer.index(...)`.
   - On a per-item failure: record a failed read/conversion stage (reason) and continue.
6. If `session.reconcilesDeletes()` and not cancelled: mark/clear resources absent from the
   manifest set (`removeDeletedResourceIndexes`).
7. `completeScan`: totals + `DONE` + `scanSuccess`; if success **and not cancelled**, advance the
   cursor in `configJson`. (Orchestrator publishes `settings-updated`.)

The engine owns: lifecycle, the diff/skip rule, the per-item pipeline, deletion reconciliation,
cancellation checks, progress, cursor persistence, partial-failure handling. None of it is
source-aware.

### 3.3 Orchestrator & coordinator (generic)

- **`KnowledgeScanCoordinator`** becomes keyed by `(KnowledgeSource, UUID rootId)` with one method
  family — `tryStart`, `finish`, `isCancelled`, `cancelAndWait` — replacing the duplicated
  `*FolderScan*` / `*JiraScan*` sets.
- **`KnowledgeIngestionService`** (one bean) replaces `KnowledgeFolderScanService` and
  `JiraProjectScanService`: resolve root → `tryStart` dedupe → mark in progress + publish → run
  the engine with the registry-selected adapter → settle failure → `finish` → publish → return the
  DTO via the existing non-synchronized mappers. Manual scan = one root; scheduled sweep = all
  non-paused roots, regardless of source.

### 3.4 Registry

`KnowledgeSourceRegistry` injects all `KnowledgeSourceAdapter` beans and maps `source() →
adapter`. The engine, orchestrator, coordinator, and retrieval never branch on source type.

## 4. Current state → target mapping

| Today | Becomes |
|---|---|
| `LocalFolderKnowledgeScanService` (scan loop) | `FolderSourceAdapter` (enumerate/fetch/isUnchanged) + engine |
| `LocalFolderKnowledgeReadService` / `…ConversionService` | folder adapter `fetch` + a `PlainTextConverter` |
| `JiraKnowledgeScanService` (scan loop) | `JiraSourceAdapter` (wraps `JiraIssueFetchService`) + engine |
| Jira inline read/convert (`successfulRead/Conversion`) | engine read-stage + `MarkdownConverter` (passthrough) |
| `KnowledgeFolderScanService` / `JiraProjectScanService` (orchestrators) | one `KnowledgeIngestionService` |
| `KnowledgeScanCoordinator` folder*/jira* methods | one `(source,id)`-keyed family |
| Jira `updatedSince` / `configWithScanMetadata` | `ScanCursor` read/write in the engine |
| Duplicated `startRootScan/startScan/completeScan/canReusePipeline/sameTimestamp/ScanStats/*ScanException` | shared in the engine |

**Kept as-is (already the shared backbone):** `KnowledgeResourcePipelineRepository`,
`KnowledgeChunkingService`, `KnowledgeIndexingService`/Lucene, `KnowledgeResource*` stage models,
the root/resource/scan repositories, `KnowledgeScanProgress`.

**Data model:** unchanged. The only per-source data stays `KnowledgeRoot.configJson` (identity +
options + cursor) plus secrets in the secret store. This is a code-structure refactor, not a
schema change.

## 5. Cross-cutting concerns (solved once in the engine/orchestrator)

- **Incremental — two levels.** (1) optional source **cursor** (`ScanCursor`) so capable sources
  filter server-side; (2) universal per-item change check (`isUnchanged` + reusable-pipeline set)
  so every source skips unchanged items even without a cursor.
- **Cancellation & dedupe** — one coordinator; cancellation checked between items by the engine.
- **Status & events** — `IN_PROGRESS/DONE/error/paused` + `settings-updated`/progress, identical
  for every source.
- **Partial failure** — a failed item records a failed stage (reason) and the scan continues; only
  root-level failures (auth/connectivity) fail the scan.
- **Config & secrets** — the adapter resolves them in `openSession(root)`; no threading of
  source-specific config through engine methods.
- **Extension points (named, not built now):** per-source **concurrency** (parallel fetch/convert)
  and **rate-limit/backoff** become engine policies once an API source needs them.

## 6. Adding a new source (worked example: GitHub)

1. `GitHubSourceAdapter implements KnowledgeSourceAdapter` — `openSession(root)` resolves the repo
   + token from `configJson`/secret store; `enumerate` lists files (cursor = last commit SHA);
   `fetch` downloads a file's bytes + `format`; `isUnchanged` compares blob SHAs.
2. Register it (`KnowledgeSource.GITHUB → GitHubSourceAdapter`).
3. Optionally add a presentation descriptor (label "GitHub", icon) for retrieval/UI cosmetics.

No engine, orchestrator, coordinator, retrieval, chunking, indexing, or converter changes. The
source inherits incremental scans, cancellation, pause, status, dedupe, deletion reconciliation,
and every registered converter.

## 7. Migration plan (incremental, tests green at each step)

1. **Generic coordinator** — key `KnowledgeScanCoordinator` by `(source, id)`; update callers.
   (Mechanical, low risk.)
2. **Converters + contracts + engine** — add `FormatConverter`s (`PlainText`, `Markdown`), the
   adapter/session/manifest/document records, and `KnowledgeScanEngine`.
3. **Jira onto the engine** — `JiraSourceAdapter` wrapping `JiraIssueFetchService`; delete the Jira
   scan loop. Keep `JiraKnowledgeScanServiceTests` behavior (adapt to the adapter/engine seam).
4. **Folder onto the engine** — `FolderSourceAdapter`; delete the folder scan loop and the
   read/conversion beans (folded into adapter + converter).
5. **Unify orchestrators** — one `KnowledgeIngestionService`; delete
   `KnowledgeFolderScanService`/`JiraProjectScanService`; repoint controllers/jobs.
6. **Registry + cleanup** — `KnowledgeSourceRegistry`; remove dead per-source plumbing.

## 8. Risks & non-goals

- **Risk: behavioral drift during extraction.** Mitigation: migrate one source at a time behind the
  same tests; preserve `isUnchanged` semantics per source (folder: size+timestamp; Jira: timestamp)
  rather than forcing a single rule.
- **Risk: Jira incremental cursor.** Keep the exact `updatedSince` (with the 24h JQL overlap) inside
  the Jira adapter; the engine only persists/passes the opaque `ScanCursor`.
- **Non-goal (now):** embeddings/vector search (Lucene stays), parallel/rate-limited fetching
  (designed-for, not implemented), and reworking retrieval beyond an optional source descriptor.
