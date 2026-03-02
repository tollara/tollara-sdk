---
name: SDK Monorepo and Multi-Language SDKs
overview: Create a new monorepo containing multi-language SDKs (JavaScript, C#, Python, Java, Go, Rust, Ruby, PHP), n8n community node, and OpenClaw plugin supporting both "caller" (invoke agents) and "backend" (receive requests from Agent Hub, verify HMAC, report usage) modes. Package naming uses a configurable placeholder (e.g. marketplace) until the product name is finalized.
todos: []
isProject: false
---

# SDK Monorepo and Multi-Language SDKs Plan

This plan is for the **SDK monorepo** (e.g. `agent-hub-sdk`). The **Java code imported from Agent Hub** lives in this repo in two places: `**client/`** (marketplace client: verifier, usage client, validation client, models) and `**lib/`** (vendored common-utils, e.g. HmacUtils). Both `**client/`** and `**lib/`** must be refactored and consolidated into `**sdk-java/`** to form the single publishable Java SDK artifact. The building agent should use the existing Java code as the **reference** for API surface, HMAC behavior, and HTTP contracts. This repo does not depend on the Agent Hub repo being open; see `docs/sdk-repo-project-context.md` for authoritative context (path prefixes, ECS vs local, etc.).

---

## 1. Repository and naming

- **Repo**: This is the SDK monorepo (e.g. `agent-hub-sdk` or `marketplace-sdks`). The Java code imported from Agent Hub lives in `**client/`** and `**lib/`**; both must be refactored into `**sdk-java/**`. There is no dependency on the Agent Hub repo.
- **Package/product name placeholder**: Use a single placeholder everywhere so you can find-replace when the product name is finalized. Suggested placeholder: `marketplace`. Replace with the final name (e.g. `agenthub`, `agent-hub`) in:
  - Package names (npm: `@marketplace/agent-sdk`, NuGet: `Marketplace.AgentSdk`, PyPI: `marketplace-agent-sdk`, Maven: `com.marketplace:agent-sdk`, etc.)
  - Namespaces, module paths, and human-facing labels in docs
- **Single shared doc**: In the repo root, add a `PACKAGE_NAME.md` or section in README: "Current package name placeholder: `marketplace`. Replace with final product name before first public release."

---

## 2. Monorepo layout

Use a flat layout with one directory per deliverable. CI and docs can live at root.

```
/
  README.md
  PACKAGE_NAME.md
  docs/                    # Optional: shared SDK docs (HMAC spec, API overview)
  client/                  # Java client from Agent Hub (reference); refactor into sdk-java/
  lib/                     # Vendored common-utils from Agent Hub (e.g. HmacUtils); refactor into sdk-java/
  sdk-java/                # Publishable Java SDK (consolidate client + lib here)
  sdk-js/
  sdk-dotnet/
  sdk-python/
  sdk-go/
  sdk-rust/
  sdk-ruby/
  sdk-php/
  integration-n8n/
  integration-openclaw/
  .github/workflows/       # Per-language publish + optional test matrix
```

Each `sdk-*` and `integration-*` folder is self-contained (own build, deps, tests). No shared polyglot code; HMAC and API behavior are specified in docs and implemented per language.

---

## 3. Reference: Agent Hub platform (source of truth)

Base URLs and path prefixes (e.g. gateway `/api` vs `/gateway/api/v1` on ECS) are configurable; see `docs/sdk-repo-project-context.md` for the full table.

The **Agent Hub** platform (separate repo) consists of:

- **Gateway service**: Validates agent keys, forwards requests to agent backends with HMAC-signed headers and optional async callbacks.
- **Core service**: Agent/key/subscription data; exposes `/api/v1/agent-keys/validate` and gateway invoke-context endpoints.
- **Usage service**: Usage reporting, progress, completion, quota.

**Key API surface for SDKs:**


| Concern                    | Endpoint / behavior                                                                                                                                                                                                                                                                                                                                               |
| -------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Invoke agent (sync)        | `POST/GET/PUT/DELETE /api/agent/{agentId}/endpoint/{endpointId}/invoke` (gateway). Client sends `Authorization: Bearer <agentKey>` and optional body.                                                                                                                                                                                                             |
| Invoke agent (async)       | Same path with `/invoke/async`; response includes `requestId`, `progressUrl`, `callbackUrl`.                                                                                                                                                                                                                                                                      |
| Validate agent key         | `POST {coreServiceUrl}/api/v1/agent-keys/validate` with body `{ "agentKey", "agentId", "agentSecret" }`. Response includes HMAC in `X-Marketplace-Signature`, `X-Marketplace-Timestamp`; body has `valid`, `userId`, `agentId`, `plan`, `roles`, `quotaRemaining`, `subscriptionActive`. Validate response HMAC as `HMAC(responseBody + timestamp, agentSecret)`. |
| Report usage (non-proxied) | `POST {usageServiceUrl}/api/usage/report` with body `{ userId, agentId, unitsUsed, timestamp }`. Headers: `X-Marketplace-Signature`, `X-Marketplace-Timestamp` (signature = HMAC(body + timestamp, agentSecret)).                                                                                                                                                 |
| Progress (async)           | POST to `progressUrl` (from async response). Body: `{ stage, percentageComplete, errorMessage?, timestamp }`. New signature for this body: HMAC(body + timestamp, agentSecret); timestamp from original callback URL query param.                                                                                                                                 |
| Completion (async)         | POST to `callbackUrl` (usage-service `/api/usage/complete/{requestId}`). Body: `{ status, result?, resultUrl?, contentType?, units?, timestamp }`. Same signing: HMAC(body + timestamp, agentSecret); timestamp from URL.                                                                                                                                         |


**Request headers from gateway to agent (backend):**

- `X-Marketplace-Signature`, `X-Marketplace-Timestamp`, `X-Marketplace-User-ID`, `X-Marketplace-Plan`, `X-Marketplace-Roles`, `X-Marketplace-Quota-Remaining`, `X-Marketplace-Subscription-Active`

---

## 4. HMAC specification (cross-language)

All SDKs must implement the same HMAC behavior so verification matches the gateway and usage-service.

**Algorithm:** HMAC-SHA256; key and message in UTF-8; output Base64-encoded.

**Inbound request (gateway → agent):**

- Canonical string to sign: `payload + timestamp + userContextString` (concatenation, no separators).
- `payload`: Raw request body as string (empty string if no body). If the platform serializes JSON, use the same byte-for-byte string (e.g. normalized JSON).
- `timestamp`: Same value as `X-Marketplace-Timestamp` (numeric string).
- `userContextString`: `userId ?? ""` + `plan ?? ""` + `roles.join(",")` + `quotaRemaining.toString()` (no separators between; nulls as empty).
- Signature: `Base64(HMAC-SHA256(canonicalString, agentSecret))`. Compare with `X-Marketplace-Signature` using constant-time compare.

**Outbound (agent → usage / progress / complete):**

- Canonical string: `bodyString + timestamp` (body = JSON string of request body; timestamp = same as header).
- Signature: same algorithm; header `X-Marketplace-Timestamp`: string of timestamp.

**Validation response (core → client):**

- Response body (JSON string) + timestamp (from response header) concatenated; then HMAC with agentSecret; compare to `X-Marketplace-Signature`.

**Replay:** Document that implementations should enforce a reasonable timestamp window (e.g. ±5 minutes) if desired; the reference Java code does not enforce it in the verifier.

Include in `docs/hmac-spec.md` (or equivalent) one or two test vectors (e.g. sample payload, timestamp, secret, expected signature) so each language can validate its implementation.

---

## 5. Unified SDK API surface (all languages)

Each language SDK must expose the same logical surface (names can follow language conventions). Use the existing Java client as the reference.


| Method / capability                                                                             | Description                                                                                                                                                                                                                    |
| ----------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **verifySignature(request)**                                                                    | Validates HMAC using headers and body. Request is language-appropriate (e.g. HTTP request object or headers + body). Returns boolean or throws/returns structured error (e.g. `SignatureError`). Use constant-time comparison. |
| **getUserContext(request)**                                                                     | Parses `X-Marketplace-`* headers into a typed object: `userId`, `plan`, `roles`, `quotaRemaining`, `subscriptionActive`.                                                                                                       |
| **validateAgentKey(key)**                                                                       | Calls core-service `/agent-keys/validate`; verifies response HMAC; returns typed result (userId, agentId, plan, roles, quotaRemaining, subscriptionActive) or null/false. Optional: cache with TTL (e.g. 60s) as in Java.      |
| **reportProgress(progressUrl, requestId, stage, percentageComplete, errorMessage?)**            | POST to progress URL; parse `signature` and `timestamp` from URL query; build body; sign with `body + timestamp`; set headers.                                                                                                 |
| **reportCompletion(callbackUrl, requestId, status, result?, resultUrl?, contentType?, units?)** | POST to callback URL; same signature extraction and signing.                                                                                                                                                                   |
| **reportUsage(userId, agentId, unitsUsed, timestamp?)**                                         | POST to usage-service report endpoint with signed body. Return typed response (status, overLimit, remaining*, etc.).                                                                                                           |


**Optional but recommended:**

- Retries with backoff for outbound HTTP (validate, report, progress, completion).
- Timeouts and configurable base URLs (gateway, core, usage).
- Structured errors: `SignatureError`, `QuotaExceededError`, `UpstreamUnavailableError`, etc.
- Idempotency/correlation IDs where the platform supports them.

**Java reference files (in this repo):** client code in `**client/`** (e.g. `client/java/src/.../MarketplaceRequestVerifier`, `UsageServiceClient`, `AgentKeyValidationClient`, models); HMAC/util code in `**lib/`** (e.g. `lib/common-utils/.../HmacUtils`).

- **MarketplaceRequestVerifier**: `verifyHmacSignature`, `buildUserContextString`, `extractUserContext`, `UserContext`.
- **UsageServiceClient**: `sendProgressUpdate`, `sendCompletion`, `reportUsage`; progress/complete URL parsing and body signing.
- **AgentKeyValidationClient**: `validateAgentKey`, request/response DTOs, response HMAC check, optional cache.
- **HmacUtils** (vendored): `calculateHmac(data, key)`, `calculateHmacWithTimestamp(data, timestamp, key)` (canonical = `data + timestamp`).
- **Models**: `UsageReportRequest`, `UsageReportResponse`.

---

## 6. Per-language SDK outline

**sdk-java**

- The Java code from Agent Hub is already in this repo under `**client/`** and `**lib/`**. **Refactor both into `sdk-java/`**: consolidate client and vendored lib into a single publishable module. Remove any remaining dependency on the Agent Hub repo; ensure HMAC logic is self-contained (vendored or inlined). GroupId/artifactId use placeholder: e.g. `com.marketplace:agent-sdk`. Publish to Maven Central (or private Maven). Optional: `agent-sdk-spring-boot-starter` with filter for signature verification and context extraction.

**sdk-js**

- TypeScript/JavaScript. Package name placeholder: `@marketplace/agent-sdk`. Publish to npm. Implement verifySignature, getUserContext, validateAgentKey, reportProgress, reportCompletion, reportUsage per HMAC spec. Optional: `@marketplace/agent-sdk-express` (middleware), `-next`, `-koa`.

**sdk-dotnet**

- C# library. Package: `Marketplace.AgentSdk`. NuGet. Same surface; optional ASP.NET Core middleware for HMAC + UserContext binding.

**sdk-python**

- Python 3. Package: `marketplace-agent-sdk`. PyPI. Same surface; optional `marketplace-agent-sdk-fastapi` or `-flask` (middleware/deps).

**sdk-go**

- Go module. Path: `github.com/<org>/agent-sdk-go`. Implement same surface; optional `http.Handler` middleware. Use Go module versioning and GitHub Releases.

**sdk-rust**

- Crate: `marketplace-agent-sdk`. crates.io. Optional feature flags for `axum`, `actix-web`, `warp` extractors/middleware.

**sdk-ruby**

- Gem: `marketplace_agent_sdk`. RubyGems. Optional Rack middleware.

**sdk-php**

- Composer: `marketplace/agent-sdk`. Packagist. Optional Laravel/PSR-15 middleware.

Each SDK should have a short README: install (one command), minimal example (verify + getUserContext, and one of reportUsage or validateAgentKey), link to shared HMAC/API docs.

---

## 7. n8n integration

**Folder:** `integration-n8n/`

**Deliverable:** n8n community node package (npm). Package name placeholder: `n8n-nodes-marketplace` (or scoped `@marketplace/n8n-nodes-marketplace`).

**Nodes to implement:**

- **Marketplace Trigger**: Webhook node that verifies HMAC (using agent secret from credentials), parses `X-Marketplace-`* headers, outputs user context and body for the workflow.
- **Marketplace Invoke**: Calls gateway `POST /api/agent/{agentId}/endpoint/{endpointId}/invoke` with agent key; optional async and polling.
- **Marketplace Progress** / **Marketplace Complete**: Send progress and completion to the URLs returned in async response (signed with agent secret).
- **Marketplace Validate Key**: Calls core-service validate endpoint; returns user/plan/quota etc.

**Credentials:** Agent secret; optional base URLs (gateway, core, usage). Store in n8n credential type.

**Distribution:** Publish to npm; document "Install community node" in n8n (name/package). Optionally provide a workflow JSON template that uses HTTP Request + code node for HMAC verification as a lighter-weight alternative.

---

## 8. OpenClaw integration (two modes)

**Folder:** `integration-openclaw/`

OpenClaw plugins are TypeScript modules, installed via `openclaw plugins install <npm-spec>`, and register agent tools and optionally skills. Plugin manifest: `openclaw.plugin.json` (id, configSchema, uiHints, etc.).

**Mode A – OpenClaw as caller (using an agent)**

- **Goal:** OpenClaw user (or the OpenClaw agent) invokes agents hosted on Agent Hub.
- **Implementation:** Plugin registers agent tools, e.g. `marketplace_call_agent` (or `marketplace.call_agent` if naming allows). Tool parameters: e.g. agentId, endpointId, body, optional async. Tool `execute` calls gateway `POST /api/agent/{agentId}/endpoint/{endpointId}/invoke` with `Authorization: Bearer <agentKey>`. Config: gateway base URL, agent key (from plugin config or OpenClaw credentials). Optional tools: `marketplace_get_run_status`, `marketplace_list_agents` if the platform exposes them.
- **Skill:** Ship a skill (e.g. `skills/marketplace/SKILL.md`) that teaches the OpenClaw agent when and how to call the tool, and how to handle errors/quota.

**Mode B – OpenClaw as backend (charged by Agent Hub)**

- **Goal:** OpenClaw instance acts as the agent backend; Agent Hub gateway forwards requests to it. OpenClaw must verify HMAC, extract user context, and report usage so the platform can charge users.
- **Implementation:**
  - **Option B1 – Gateway HTTP handler in plugin:** Plugin registers a Gateway HTTP handler that receives POST requests (e.g. from a reverse proxy or a tunnel that forwards gateway traffic). Handler reads body and headers, verifies HMAC (same spec as SDK), extracts user context, then invokes the OpenClaw agent or a workflow with that context; on completion, calls usage-service report (or progress/complete if async). Handler must be able to read agent secret and usage-service URL from config.
  - **Option B2 – SDK inside OpenClaw:** Use the **JS/TS SDK** (`@marketplace/agent-sdk`) inside the plugin: a small HTTP server or handler that receives the gateway request, calls `verifySignature(request)` and `getUserContext(request)`, runs the agent logic, then calls `reportUsage` (and optionally progress/complete). The plugin provides the HTTP surface that the gateway targets; config includes agent secret, usage-service URL, and optionally core-service for validate.

**Config schema (single plugin, both modes):**

- `mode`: `caller` | `backend` (or two separate plugins if preferred).
- **Caller:** `gatewayUrl`, `agentKey`, optional `coreServiceUrl`, `usageServiceUrl` for status/completion if needed.
- **Backend:** `agentSecret`, `usageServiceUrl`, optional `coreServiceUrl`; optional base path for handler.

**Distribution:** Publish npm package (e.g. `@marketplace/openclaw` or `openclaw-marketplace`). Document: `openclaw plugins install @marketplace/openclaw`; configure `plugins.entries.<id>.config` with mode and URLs/keys. List skill directory in manifest so OpenClaw loads the skill for Mode A.

---

## 9. Documentation and versioning

- **In this repo:** `docs/hmac-spec.md` (and optionally `docs/api-overview.md`) so every SDK and integration can stay aligned. README at root: list of SDKs and integrations, one-line install per language, link to HMAC spec and (if applicable) to the main platform docs.
- **Versioning:** Keep SDK/integration versions aligned (e.g. same major.minor) and document "SDK vX.Y ↔ Platform API vX.Y" in README or a VERSIONS.md.
- **Product name:** When finalized, replace `marketplace` (or chosen placeholder) across the monorepo and update `PACKAGE_NAME.md`.

---

## 10. Build and CI (minimal)

- Each `sdk-`* and `integration-`* has its own build (Gradle, npm, pip, go build, cargo, gem, composer). Root README lists commands per folder.
- GitHub Actions: per-directory workflows (or matrix) to run tests and publish on tag or main. Publish to npm, NuGet, PyPI, Maven Central, crates.io, RubyGems, Packagist as appropriate. Java: can run `gradle publish` from `sdk-java` (with credentials from secrets).
- No need to build all languages in one job; separate workflows per language are fine.

---

## 11. Implementation order suggestion

1. Add `docs/hmac-spec.md` and `docs/api-overview.md` (and test vectors) so all implementers share one spec.
2. **sdk-java**: Refactor `**client/`** and `**lib/`** into `**sdk-java/`**; remove any agent-hub dependency; publish under placeholder group/artifact.
3. **sdk-js**: Implement full surface + HMAC; then use it in n8n and OpenClaw.
4. **integration-n8n**: Implement nodes; use JS SDK or reimplement HMAC in Node.
5. **integration-openclaw**: Plugin with Mode A (tools + skill), then Mode B (handler or SDK-based backend).
6. Remaining SDKs (C#, Python, Go, Rust, Ruby, PHP) in any order, each with tests and README.

---

## 12. Checklist for the building agent

- Ensure root README + PACKAGE_NAME.md exist.
- Add `docs/hmac-spec.md` (and optional `api-overview.md`) with test vector(s).
- sdk-java: Refactor `**client/`** and `**lib/`** into `**sdk-java/`**; remove any agent-hub dependency; publish with placeholder groupId/artifactId.
- Implement each of sdk-js, sdk-dotnet, sdk-python, sdk-go, sdk-rust, sdk-ruby, sdk-php with the unified API surface and HMAC spec.
- Implement integration-n8n (nodes + credentials).
- Implement integration-openclaw (Mode A: tools + skill; Mode B: backend handler or SDK usage).
- Add per-folder READMEs and root table of install commands.
- Add CI workflows for test and publish.
- Before release: replace package name placeholder with final product name.

