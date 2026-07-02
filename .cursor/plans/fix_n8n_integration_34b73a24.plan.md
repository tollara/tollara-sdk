---
name: Fix n8n integration
overview: Bring `integration-n8n` in line with `docs-sdk/MAIN-SDK-API-SPEC.md` by unifying all nodes on an optional `apiUrl` credential (blank = SDK prod default), refactoring Invoke onto `@tollara/service-sdk`, and adding missing caller/backend nodes. No sdk-js changes; path prefixes stay SDK-internal.
todos:
  - id: unify-credentials
    content: Remove gatewayUrl; make apiUrl optional (empty = SDK prod default); add getTollaraCredentials helper; bump to 0.1.0
    status: pending
  - id: refactor-invoke
    content: Refactor TollaraInvoke to use invokeService with optional baseUrl, method + async options
    status: pending
  - id: add-polling-nodes
    content: Add TollaraJobStatus and TollaraJobResult nodes using getRequestStatus/getRequestResult
    status: pending
  - id: add-usage-nodes
    content: Add TollaraReportUsage and TollaraEstimateUsage nodes (service-key estimate only)
    status: pending
  - id: update-package-readme
    content: Register new nodes in package.json; update README and tsconfig; verify CI build passes
    status: pending
isProject: false
---

# Fix Tollara n8n Integration

## Goals

- **Optional `apiUrl`** credential — when blank, pass `undefined` to SDK; SDK resolves to **`https://api.tollara.ai`** via `resolveBaseUrl` (no hardcoded default in n8n UI)
- Remove **`gatewayUrl`** from credentials and Invoke node
- Delegate all HTTP calls to **`@tollara/service-sdk`** (no raw `fetch` in nodes)
- SDK applies fixed path prefixes internally (`/api`, `/api/v1`, `/api/usage`) — users never configure these
- Close spec gaps: async invoke, job status/result polling, usage report, service-key estimate
- Update README and package registration

## Design decisions

- **`apiUrl` is optional.** Only **Service Secret** is required in credentials. End users on prod leave API URL empty.
- **No sdk-js changes.** Path prefix overrides (ECS `/gateway/api/v1`, etc.) are out of scope; SDK defaults are correct for prod `api.tollara.ai`.
- **No path-prefix credential fields.** Without sdk-js support on all functions, exposing prefixes would be misleading (only Invoke could partially honor them today).
- **Progress / Complete unchanged.** They use full `progressUrl` / `callbackUrl` from async invoke responses; origin and path prefixes in credentials are irrelevant.

## Out of scope

- **JWT usage estimate** (§2.2) — service-key estimate only
- **SSE / PER_TIME_UNIT streaming** (§1.5)
- **Path prefix overrides** — deferred until sdk-js reaches Java parity (`corePathPrefix`, `gatewayPathPrefix`, `usagePathPrefix` on all clients)
- **sdk-js changes** — integration-only work
- **integration-openclaw** — not touched

## Architecture after changes

```mermaid
flowchart LR
  subgraph credentials [TollaraApi credentials]
    apiUrl["apiUrl optional blank = prod"]
    serviceSecret[serviceSecret required]
  end

  subgraph callerNodes [Caller nodes]
    Invoke[TollaraInvoke]
    Validate[TollaraValidateKey]
    Estimate[TollaraEstimateUsage]
    Status[TollaraJobStatus]
    Result[TollaraJobResult]
  end

  subgraph backendNodes [Backend nodes unchanged URLs]
    Trigger[TollaraTrigger]
    Progress[TollaraProgress]
    Complete[TollaraComplete]
    Report[TollaraReportUsage]
  end

  subgraph sdk [@tollara/service-sdk unchanged]
    invokeService
    validateServiceKey
    estimateUsage
    getRequestStatus
    getRequestResult
    reportUsage
    reportProgress
    reportCompletionFull
    verifySignatureFromHeaders
  end

  credentials --> callerNodes
  credentials --> backendNodes
  Invoke --> invokeService
  Validate --> validateServiceKey
  Estimate --> estimateUsage
  Status --> getRequestStatus
  Result --> getRequestResult
  Report --> reportUsage
  Progress --> reportProgress
  Complete --> reportCompletionFull
  Trigger --> verifySignatureFromHeaders
```

---

## 1. Unify credentials

**File:** [integration-n8n/credentials/TollaraApi.credentials.ts](integration-n8n/credentials/TollaraApi.credentials.ts)

- Remove **`gatewayUrl`** entirely
- **`serviceSecret`** — required
- **`apiUrl`** — optional, **not required**
  - Default: empty string `''`
  - Placeholder: `https://api.tollara.ai`
  - Description: leave blank for production; override only for local/dev testing (e.g. `http://localhost:8083`)

**Shared helper:** [integration-n8n/lib/tollaraCredentials.ts](integration-n8n/lib/tollaraCredentials.ts)

```typescript
export function getTollaraCredentials(credentials: IDataObject): {
  apiUrl: string | undefined;  // undefined when blank → SDK uses DEFAULT_API_URL
  serviceSecret: string;
}
```

Implementation: `const raw = (credentials.apiUrl as string)?.trim(); return { apiUrl: raw || undefined, serviceSecret }`.

All nodes pass `baseUrl: apiUrl` (undefined when omitted) to SDK functions. SDK already handles fallback:

```typescript
// sdk-js/src/urls.ts
resolveBaseUrl(baseUrl, DEFAULT_API_URL)  // DEFAULT_API_URL = 'https://api.tollara.ai'
```

**Breaking change:** workflows using `gatewayUrl` must remove it; leave `apiUrl` blank for prod or set a dev origin. Bump package version to **`0.1.0`**.

---

## 2. Refactor Tollara Invoke

**File:** [integration-n8n/nodes/TollaraInvoke/TollaraInvoke.node.ts](integration-n8n/nodes/TollaraInvoke/TollaraInvoke.node.ts)

Replace raw `fetch` + `gatewayUrl` with `invokeService` from `@tollara/service-sdk`:

```typescript
import { invokeService, type GatewayHttpMethod } from '@tollara/service-sdk';
```

**New node parameters:**

| Parameter | Type | Default | Spec |
|-----------|------|---------|------|
| HTTP Method | options: GET, POST, PUT, DELETE | POST | §1.1–1.2 |
| Async | boolean | false | §1.2 (`/invoke/async`) |
| Service Key | string (password) | — | Bearer auth |
| Service ID | string | — | path param |
| Endpoint ID | string | — | path param |
| Body | string | `{}` | optional JSON for POST/PUT |

**Output shape:**

```json
{
  "statusCode": 200,
  "body": "...",
  "data": {},
  "requestId": "...",
  "callbackUrl": "...",
  "progressUrl": "..."
}
```

- Map `asyncEnvelope` from SDK when status is 202
- Parse JSON body into `data` when valid (same pattern for polling nodes)

Call: `invokeService({ baseUrl: apiUrl, method, serviceId, endpointId, serviceKey, body, async })` — no `gatewayPathPrefix`; SDK default `/api` applies.

---

## 3. Add missing nodes

One folder per node; register in [integration-n8n/package.json](integration-n8n/package.json) → `n8n.nodes`.

### 3a. Tollara Job Status (§1.3)

**New:** `integration-n8n/nodes/TollaraJobStatus/TollaraJobStatus.node.ts`

- SDK: `getRequestStatus({ baseUrl: apiUrl, requestId, serviceKey })`
- Parameters: **Service Key**, **Request ID**
- Output: `{ statusCode, ok, body, data }`

### 3b. Tollara Job Result (§1.4)

**New:** `integration-n8n/nodes/TollaraJobResult/TollaraJobResult.node.ts`

- SDK: `getRequestResult({ baseUrl: apiUrl, requestId, serviceKey })`
- Parameters: **Service Key**, **Request ID**
- Output: same shape as Job Status

### 3c. Tollara Report Usage (§3.1)

**New:** `integration-n8n/nodes/TollaraReportUsage/TollaraReportUsage.node.ts`

- SDK: `reportUsage({ baseUrl: apiUrl, userId, serviceId, unitsUsed, serviceSecret })`
- Parameters: **User ID**, **Service ID**, **Units Used**
- Output: `UsageReportResponse` fields

### 3d. Tollara Estimate Usage (§2.3)

**New:** `integration-n8n/nodes/TollaraEstimateUsage/TollaraEstimateUsage.node.ts`

- SDK: `estimateUsage({ baseUrl: apiUrl, serviceKey, serviceId, serviceSecret, estimatedUnits })`
- Parameters: **Service Key**, **Service ID** (optional), **Estimated Units**
- Output: `UsageEstimateResult` fields

---

## 4. Existing nodes (minimal touch)

| Node | Change |
|------|--------|
| **Tollara Trigger** | Refactor to `getTollaraCredentials` helper only (logic unchanged) |
| **Tollara Validate Key** | Use helper; pass optional `baseUrl: apiUrl` |
| **Tollara Progress** | Use helper for `serviceSecret` only; **no URL changes** — still uses full `progressUrl` param |
| **Tollara Complete** | Use helper for `serviceSecret` only; **no URL changes** — still uses full `callbackUrl` param |

Progress and Complete do not read `apiUrl`; async responses supply absolute URLs.

---

## 5. Package and docs updates

**File:** [integration-n8n/package.json](integration-n8n/package.json)

- Bump version to **`0.1.0`**
- Register 4 new nodes in `n8n.nodes`

**File:** [integration-n8n/README.md](integration-n8n/README.md)

- Credentials: **Service Secret** (required) + **API URL** (optional; blank = production)
- List all 9 nodes; remove stale Core/Usage URL and quota references
- **Upgrade from 0.0.1:** `gatewayUrl` removed; leave API URL blank for prod or set dev origin
- Example workflows:

```
Caller async:  Tollara Invoke (async) → Tollara Job Status → Tollara Job Result
Backend async: Tollara Trigger → [logic] → Tollara Progress → Tollara Complete
```

**File:** [integration-n8n/tsconfig.json](integration-n8n/tsconfig.json) — add `"lib/**/*.ts"` to `include`

---

## 6. Verification

- CI unchanged: build `sdk-js` then `integration-n8n` (sdk-js is dependency only; no edits expected)
- Local:

```powershell
cd sdk-js; npm run build
cd ..\integration-n8n; npm ci; npm run build
```

- No new n8n test suite (SDK functions tested in `sdk-js`)

---

## Files changed (summary)

| Action | Path |
|--------|------|
| Edit | `integration-n8n/credentials/TollaraApi.credentials.ts` |
| Edit | `integration-n8n/nodes/TollaraInvoke/TollaraInvoke.node.ts` |
| Edit | `integration-n8n/nodes/TollaraValidateKey/TollaraValidateKey.node.ts` |
| Edit | `integration-n8n/nodes/TollaraTrigger/TollaraTrigger.node.ts` (helper only) |
| Edit | `integration-n8n/nodes/TollaraProgress/TollaraProgress.node.ts` (helper only) |
| Edit | `integration-n8n/nodes/TollaraComplete/TollaraComplete.node.ts` (helper only) |
| Add | `integration-n8n/lib/tollaraCredentials.ts` |
| Add | `integration-n8n/nodes/TollaraJobStatus/TollaraJobStatus.node.ts` |
| Add | `integration-n8n/nodes/TollaraJobResult/TollaraJobResult.node.ts` |
| Add | `integration-n8n/nodes/TollaraReportUsage/TollaraReportUsage.node.ts` |
| Add | `integration-n8n/nodes/TollaraEstimateUsage/TollaraEstimateUsage.node.ts` |
| Edit | `integration-n8n/package.json` |
| Edit | `integration-n8n/README.md` |
| Edit | `integration-n8n/tsconfig.json` |

Rebuild **`integration-n8n`** after changes. **`sdk-js`** rebuild only if dependency resolution requires it (no source changes planned).
