---
name: Main Website SDK and Integrations Docs
overview: Add a public docs site to the agent-hub repo (docs-site/) and populate it with documentation for the platform, SDKs, and integrations (n8n, OpenClaw). The existing documentation/ folder remains private notes; the new site is the single public face for agent owners.
todos: []
isProject: false
---

# Main Website: SDK and Integrations Documentation

This plan covers adding and maintaining **SDK and integrations documentation on the main website**. The website source lives in the **agent-hub** (platform) repo; the SDK code and per-language READMEs live in a separate **SDK monorepo**. The main site is the canonical place for "how to get started" and "where to find the SDK and integrations."

---

## 1. Scope and repo boundaries


| Location                         | Purpose                                                                                                                        |
| -------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| **agent-hub repo**               | Platform code + **public docs site** (this plan).                                                                              |
| **documentation/** (agent-hub)   | Your private notes; **not** built into the website. Leave as-is.                                                               |
| **docs-site/** (agent-hub, new)  | Source for the **public website**. Build output is deployed (e.g. GitHub Pages, S3, or your host).                             |
| **SDK monorepo** (separate repo) | SDK code, per-language READMEs, HMAC spec, n8n node, OpenClaw plugin. Deep "how to use the SDK in Python/Go/etc." lives there. |


The main website will **link out** to the SDK repo (and to npm/PyPI/etc.) for install commands and detailed SDK docs; it will **host** overview pages, concepts, and integration guides that are product-facing and stable.

---

## 2. Docs-site setup in agent-hub

**Add a dedicated docs site** under the agent-hub repo root.

- **Recommended tool:** Docusaurus (React, good nav/search/versioning) or MkDocs (Markdown-only, simpler). Alternative: VitePress. Choose one and stick with it.
- **Directory:** `docs-site/` at repo root.
- **Content directory:** `docs-site/docs/` (Docusaurus) or `docs-site/docs/` (MkDocs) for Markdown pages. Do **not** point the generator at `documentation/` so private notes are never published.
- **Config:** `docs-site/docusaurus.config.js` (or `mkdocs.yml`) defines site title, base URL, theme, nav. Nav should include top-level sections: **Getting started**, **Concepts**, **API overview**, **SDKs**, **Integrations**, and optionally **Reference** or **Changelog**.

**Build and deploy:**

- **Local:** From repo root, e.g. `cd docs-site && npm install && npm run build` (or `mkdocs build`). Use `npm run start` or `mkdocs serve` for local preview.
- **CI (e.g. GitHub Actions):** On push to `main` (and optionally on release tags), run the docs build from `docs-site/`, then deploy the built output to your hosting (GitHub Pages, S3 + CloudFront, Netlify, etc.). Keep deployment steps in a single workflow file under `.github/workflows/` (e.g. `docs-deploy.yml`).

---

## 3. Site structure (nav and main sections)

Suggested top-level navigation. Exact labels can match your product name (e.g. "Agent Hub" or final name).

- **Getting started**
  - Introduction (what the platform is, who it’s for)
  - Quick start (e.g. create an agent, get an agent key, first invoke)
  - Authentication (agent keys, scopes, security)
- **Concepts**
  - Agents and endpoints
  - Billing and usage (subscriptions, quotas, usage reporting)
  - Sync vs async invocation
  - Request signing (HMAC) and headers (high-level; link to SDK repo for full spec)
- **API overview**
  - Gateway: invoke (sync/async), base URL, auth
  - Core: validation, invoke-context (brief)
  - Usage: report, progress, complete (brief)
  - Link to OpenAPI/Swagger if you expose it (e.g. gateway, core, usage)
- **SDKs**
  - Overview: what the SDK does (verify signature, get user context, validate key, report usage/progress/completion)
  - Install by language: one subsection per language (JavaScript, C#, Python, Java, Go, Rust, Ruby, PHP) with:
    - **One-line install** (e.g. `npm install @marketplace/agent-sdk`, `pip install marketplace-agent-sdk`, `implementation 'com.marketplace:agent-sdk'`)
    - **Link** to the SDK repo (e.g. GitHub) and, if applicable, to the package registry
    - Optional: minimal "Hello World" snippet (verify + getUserContext) inline or link to SDK repo
  - Optional: "SDK version compatibility" (e.g. SDK v1.x ↔ Platform API v1)
- **Integrations**
  - **n8n:** What the integration does (trigger + invoke + progress/complete nodes). How to install (community node name/package). Link to SDK repo or npm for the node. Optional: short "Configure credentials" and "First workflow" steps.
  - **OpenClaw:** Two modes:
    - **Using agents (caller):** OpenClaw calls your gateway; install plugin, configure gateway URL and agent key; use tools (e.g. `marketplace_call_agent`) and skill.
    - **As agent backend:** OpenClaw receives requests from your gateway; verify HMAC, report usage; link to SDK repo or plugin docs for backend setup.
  - Link to OpenClaw plugin npm package and SDK repo.
- **Reference** (optional)
  - Changelog, versioning policy, support/contact

Use a **single doc page per integration** (e.g. `integrations/n8n.md`, `integrations/openclaw.md`) so the site stays easy to maintain and link to.

---

## 4. Content that must stay in sync

- **Package/product name:** The main site will show install commands (e.g. `@marketplace/agent-sdk`). When you finalize the product name, do a pass to replace the placeholder in both the SDK monorepo and **docs-site** (and any generated API docs).
- **Gateway/Core/Usage base paths and auth:** If you change API paths or auth (e.g. header names), update the "API overview" and "Concepts" pages and any code snippets.
- **SDK version ↔ Platform version:** If you document compatibility (e.g. "SDK 1.2 supports Platform API 1.x"), keep that table or sentence updated when you cut releases.

The SDK repo remains the source of truth for **HMAC spec**, **per-language API surface**, and **detailed examples**. The main site links to it; avoid duplicating long spec text in both places.

---

## 5. What to add under docs-site/docs (concrete files)

Create (or generate) at least the following under `docs-site/docs/` (paths are examples; adjust to your generator’s conventions):

- `intro.md` – Introduction and quick start.
- `concepts/agents-and-endpoints.md`, `concepts/billing-and-usage.md`, `concepts/request-signing.md` – Concepts.
- `api/gateway.md`, `api/core.md`, `api/usage.md` – API overview (short; link to OpenAPI if available).
- `sdks/overview.md` – What the SDK provides; list of languages.
- `sdks/install.md` or one file per language (e.g. `sdks/javascript.md`, `sdks/java.md`, …) – One-line install + link to SDK repo and registry.
- `integrations/n8n.md` – n8n integration (install, credentials, first workflow; link to SDK repo/npm).
- `integrations/openclaw.md` – OpenClaw caller and backend modes; install plugin; link to plugin npm and SDK repo.

Configure the sidebar/nav in Docusaurus or MkDocs so "SDKs" and "Integrations" are easy to find from the homepage.

---

## 6. Optional: API reference from OpenAPI

If the gateway (or core/usage) exposes OpenAPI (e.g. `/v3/api-docs`), you can:

- **Option A:** Add a "Reference" section that links to the deployed Swagger UI or Redoc (e.g. at `docs.yoursite.com/api/gateway`).
- **Option B:** Use a Docusaurus plugin (or MkDocs plugin) to import OpenAPI and generate Markdown pages under `docs-site/docs/reference/`. That keeps the reference inside the same site and versioned with the docs.

Do this only if you want the reference on the main site; otherwise, linking to each service’s Swagger UI is enough.

---

## 7. CI and deployment

- **Workflow:** One workflow (e.g. `.github/workflows/docs-deploy.yml`) that:
  - Checks out agent-hub.
  - Installs dependencies for `docs-site/` (e.g. `cd docs-site && npm ci` or `pip install mkdocs mkdocs-material`).
  - Builds the site (`npm run build` or `mkdocs build`).
  - Deploys the build artifact (e.g. to `gh-pages` branch, or S3, or Netlify) so the live site updates on every merge to `main` (or only on tag push, if you prefer).
- **Branch/tag strategy:** Decide whether the live site always reflects `main` or only release tags; document it in the workflow or in a short "Contributing to docs" note in the repo.

---

## 8. Summary checklist

- Add `docs-site/` to agent-hub with chosen generator (Docusaurus / MkDocs / VitePress) and config (title, base URL, nav).
- Create docs content: intro, concepts, API overview, SDKs (overview + install per language with links to SDK repo), Integrations (n8n, OpenClaw with both modes).
- Ensure `documentation/` is never included in the docs build.
- Add GitHub Actions workflow to build and deploy the docs site.
- When product name is finalized, replace placeholder in docs-site and SDK monorepo.
- Optionally add API reference (OpenAPI/Swagger) under Reference and keep SDK version compatibility note updated.

This gives you a single place (the main website) where agent owners find platform concepts, API overview, SDK install commands, and integration guides, with deep links to the SDK repo and registries for implementation details.