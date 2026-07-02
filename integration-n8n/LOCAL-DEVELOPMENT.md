# Local development — n8n-nodes-tollara

This document is for **maintainers** working in the [tollara-sdk](https://github.com/tollara/tollara-sdk) monorepo. End users install **`n8n-nodes-tollara`** from npm via n8n Community Nodes; they do not need this setup.

## Build and test

```bash
cd integration-n8n
npm install
npm run build
npm test
```

`npm test` runs `build`, compiles unit tests from `../tests/integration-n8n/`, then runs them.

## Local n8n (Docker)

Self-hosted n8n can load unverified community nodes. The Docker setup bind-mounts your built `integration-n8n` folder. `@tollara/service-sdk` is a **devDependency** only — it is bundled into `dist/lib/tollaraSdk.js` at build time for the published package.

```powershell
cd integration-n8n
.\deploy-local.ps1
```

Or: `npm run deploy:local`

Options: `-RunTests` (run unit tests before deploy), `-SkipPull` (skip `docker compose pull`).

The same script is available as `docker\start.ps1` for backward compatibility.

Open **http://localhost:5678**, create an owner account, then add nodes — search **Tollara**.

Workflow data persists in the `n8n_data` Docker volume. The Tollara package is loaded from your repo via bind mount, not from npm. Published npm installs include demo workflows under `example-workflows/` inside the package directory.

Stop: `docker compose down` (from the `docker` folder).

## Local fixture from e2e setup (agent-hub)

After running `agent-hub` `:e2e-tests-java:n8nIntegrationSetup -PrunE2eTests`:

```powershell
cd integration-n8n
npm run apply:local-fixture -- --fixture ..\agent-hub\e2e-tests-java\build\n8n-integration\local-fixture.json
```

Import workflows from **`example-workflows/local/`** instead of the generic `example-workflows/` copies. Set `N8N_LOCAL_FIXTURE_PATH` to skip `--fixture`.

## Troubleshooting: broken Tollara nodes after import

If Tollara nodes appear in the node picker but imported workflows show **“Install this node to use it”**:

1. Run **`.\deploy-local.ps1`** (not just `npm run build`) — this rebuilds, restarts n8n, and syncs the community-node registry.
2. **Delete** the broken workflow and **import again** from `example-workflows/`. n8n strips node parameters when the package failed to load on the first import.
3. Replace **`YOUR_SERVICE_SECRET`** (and **`YOUR_SERVICE_ID`** on Validate Key) on each Tollara node. Enable **Set API Endpoints** only for local Docker API URLs.

Cause: the package must expose a root **`index.js`** (referenced by `package.json` `"main"`). Without it, n8n lists nodes in Settings but does not load them at runtime.

## Troubleshooting: red exclamation on Tollara nodes

Tollara nodes do **not** use n8n credentials. If you still see **Set Credential**, **Unnamed credential**, or a warning until you create a credential, your workflow has stale data from an older package version.

**Fix:**

1. Run **`.\deploy-local.ps1`** to load the current package version.
2. **Delete** the workflow and re-import from `example-workflows/` (do not import over an existing copy).
3. Set **Service Secret** on each Tollara node.
4. You can **delete** any old **Tollara Environment** credentials from Settings → Credentials — they are no longer used.
5. Hard-refresh the browser (Ctrl+F5).

If only one node is affected: delete it on the canvas, drag a fresh Tollara node from the picker, reconnect wires, and paste parameters back.

You do **not** need to delete the Docker image. Only reset the `n8n_data` volume if you want a completely fresh n8n instance (that wipes users and all workflows).

## Lint and publish to npm

```bash
npm run lint
npm test
```

### Verified community node release (GitHub Actions + provenance)

1. Configure npm **Trusted Publisher** for `n8n-nodes-tollara` → repo `tollara/tollara-sdk`, workflow file `publish.yml`.
2. From `integration-n8n`, run `npm run release` — bumps version, tags `n8n-nodes-tollara-vX.Y.Z`, pushes; CI publishes with provenance.
3. Submit at [n8n Creator Portal](https://creators.n8n.io/nodes) after the provenance publish succeeds.

`@tollara/service-sdk` must remain a **devDependency** (bundled at build; no runtime `dependencies`).

Scan locally (after publishing):

```bash
npx @n8n/scan-community-package n8n-nodes-tollara
```
