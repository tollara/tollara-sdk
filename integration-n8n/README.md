# n8n Community Nodes – Tollara

n8n nodes for [Tollara](https://tollara.ai): verify inbound gateway traffic, invoke listed services, poll async jobs, and report usage.

**Package:** [`n8n-nodes-tollara`](https://www.npmjs.com/package/n8n-nodes-tollara) (uses [`@tollara/service-sdk`](https://www.npmjs.com/package/@tollara/service-sdk))

## Install in n8n

Self-hosted n8n only (Community Nodes must be enabled). n8n Cloud may block unverified community packages.

1. Open **Settings → Community Nodes**.
2. Install **`n8n-nodes-tollara`**.
3. Search the node picker for **Tollara**.

No separate n8n credential is required — set **Service Secret** (and other fields) on each node.

## Nodes

Tollara integrations fall into two roles:

- **Backend (seller)** — your n8n workflow **receives** traffic from the Tollara gateway (Webhook) or validates direct callers, then runs your logic and reports usage or async progress.
- **Subscriber (buyer)** — your workflow **calls** another party’s listed service via the gateway (invoke, estimate, poll async jobs).

### Backend nodes

Use these when n8n is the **service backend** (proxied webhook or non-proxied API).

| Node | Purpose |
|------|---------|
| **Tollara Verify Request** (v4) | After a Webhook node: verify gateway HMAC and subscription access. Outputs **Allowed** or **Denied** (`tollaraErrorCode`, `tollaraHttpStatus`). Enable **Raw Body** on the Webhook. |
| **Tollara Validate Key** (v4) | Validate a caller’s service key (typical non-proxied pattern). **Allowed** / **Denied** / **Error** (503 for seller misconfig or Core unavailable). |
| **Tollara Estimate Usage** | After auth: check whether the caller has quota/cap for **N units** before expensive work (`wouldAllow`, `estimatedCost`, `breakdown`). Especially useful on **non-proxied** backends; optional on proxied when you bill variable units (tokens, time) before reporting. |
| **Tollara Progress** | Send async job progress to the `progressUrl` from the gateway invoke response. |
| **Tollara Complete** | Send async completion to the `callbackUrl` from the gateway invoke response. |
| **Tollara Report Usage** | Report billable units to the Usage API (non-proxied backends). |

**Typical proxied async backend:** Webhook → **Verify Request** → [your logic] → **Progress** → [your logic] → **Complete**

**Typical non-proxied backend:** Webhook → **Validate Key** → **Estimate Usage** → [your logic] → **Report Usage**

### Subscriber nodes

Use these when n8n **consumes** a Tollara listing (you hold a service key for that product).

| Node | Purpose |
|------|---------|
| **Tollara Invoke** | Call a listed endpoint (sync or async). Emits `tollaraOk`, `statusCode`, parsed `data`, and async `requestId` / URLs when applicable. |
| **Tollara Estimate Usage** | Optional pre-invoke check: would this call be allowed for **N units**, and what is the estimated cost? Branch on `wouldAllow` before **Invoke**.
| **Tollara Job Status** | Poll async job status by `requestId`. |
| **Tollara Job Result** | Fetch async job result by `requestId`. |

**Typical async subscriber:** **Invoke** (async) → **Job Status** → **Job Result**

**Optional pre-check:** **Estimate Usage** → IF `wouldAllow` → **Invoke** (see `subscriber-proxied-sync-agent-estimate.json`)

Set **Service Key**, **Service ID**, and **Endpoint ID** on Invoke (from the listing in the Tollara app). Job Status/Result only need the service key and request ID.

## Example workflows

Demo workflow JSON files are **bundled in the npm package** (generic templates with `YOUR_*` placeholders — not the maintainer `local/` folder).

After installing **`n8n-nodes-tollara`**, import from:

| Install context | Path |
|-----------------|------|
| **n8n Community Nodes** (typical self-hosted) | `~/.n8n/nodes/node_modules/n8n-nodes-tollara/example-workflows/` |
| Windows (Community Nodes) | `%USERPROFILE%\.n8n\nodes\node_modules\n8n-nodes-tollara\example-workflows\` |
| npm dependency in a project | `node_modules/n8n-nodes-tollara/example-workflows/` |

Use n8n **Workflow menu → Import from File** and pick a `.json` file from that folder. Replace `YOUR_SERVICE_SECRET`, `YOUR_SERVICE_KEY`, and other placeholders before activating. See `example-workflows/README.md` in the same directory for setup notes.

| Workflow | Role |
|----------|------|
| `backend-url-metadata-sync.json` | Backend — proxied sync |
| `backend-topic-brief-async.json` | Backend — proxied async |
| `backend-echo-non-proxied.json` | Backend — non-proxied |
| `subscriber-proxied-sync-agent.json` | Subscriber — proxied sync |
| `subscriber-proxied-sync-agent-estimate.json` | Subscriber — estimate + invoke |
| `subscriber-proxied-async-agent.json` | Subscriber — async + poll |
| `subscriber-non-proxied-sync-agent.json` | Subscriber — direct HTTP (no gateway invoke) |

Point your listing **realUrl** (backend) or subscriber **Set Config** values at your n8n or agent URLs as described in each workflow’s sticky notes and the bundled `example-workflows/README.md`.

## API endpoints

**Production:** leave **Set API Endpoints** disabled on each node.

**Non-production / custom hosts:** enable **Set API Endpoints** on nodes that call Tollara APIs and set the gateway, core, and usage base URLs for your environment.

Set **Service Secret** and **Service ID** on each node where those fields appear.

## Developing this package

Maintainers: build, test, Docker-based local n8n, and e2e fixture workflows are documented in [LOCAL-DEVELOPMENT.md](LOCAL-DEVELOPMENT.md).
