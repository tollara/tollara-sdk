# Tollara SDK (JavaScript/TypeScript)

**Package:** `@tollara/service-sdk` (version **0.0.1** in this repo)

Verify inbound HMAC, validate **service keys**, run usage pre-flight (service-key **and** JWT paths), **gateway invoke** (sync/async), report usage, progress, completion, and poll async job status.

This README covers the public SDK contract and usage examples.

## API origin

By default, the SDK uses the production Tollara API origin. Override when needed (non-production or private deployments):

- **`TollaraClient`:** pass `apiUrl`, or set **`TOLLARA_API_URL`**

## Tollara client (recommended)

`TollaraClient` uses optional **`TOLLARA_SERVICE_ID`** (service UUID), required **`TOLLARA_SERVICE_SECRET`** (unless passed as `serviceSecret`), and optional **`TOLLARA_API_URL`**.

```ts
import { TollaraClient } from '@tollara/service-sdk';

const client = new TollaraClient({
  serviceId: 'service-uuid',
  serviceSecret: 'secret',
});
await client.getRequestStatus(requestId, serviceKey);
await client.reportUsage(userId, serviceId, 1);
await client.validateServiceKey(serviceKey);
const estimate = await client.estimateUsage(serviceKey, 1);
if (estimate) {
  const allowed = estimate.wouldAllow;
  const status = estimate.httpStatus;
}

// JWT usage estimate (unsigned Core response): internal Core user id + service id + units
// await client.estimateUsageWithJwt(bearerJwt, coreUserId, serviceId, 1);

// Gateway invoke (Bearer = service key): method, serviceId, endpointId, serviceKey, optional body + async flag
// await client.invokeService('POST', serviceId, endpointId, serviceKey, { body: '{}', async: false });
```

### Verify signature and user context together

Verification defaults to signing version **v2** (newer user-context suffix, no quota segment in the signed material). `verifySignatureFromHeaders` also reads `X-Tollara-Signing-Version` when present.

```ts
import { verifySignatureFromHeadersAndGetUserContext } from '@tollara/service-sdk';

const ctx = verifySignatureFromHeadersAndGetUserContext(serviceSecret, headers, rawBody);
if (ctx) { /* trusted */ }
```

## Install

```bash
npm install @tollara/service-sdk
```

## API highlights

- `TollaraHeaders` — canonical `X-Tollara-*` names (including signing-version for gateway HMAC v2)
- `buildGatewayUserContextString` / `buildGatewayUserContextStringV2` — inbound suffix helpers
- `verifyInboundHmac` / `verifySignatureFromHeaders` — inbound gateway HMAC
- `getUserContext` — parses headers (case-insensitive keys)
- `TollaraClient` — validate key, estimates, invoke, usage reporting, gateway polling
- `validateServiceKey` / `estimateUsage` — Core **service-key** paths; response HMAC verified when headers present
- `estimateUsageWithJwt` — Core `POST …/billing/usage/estimate` with Bearer JWT (unsigned response)
- `invokeService` — gateway `…/service/{serviceId}/endpoint/…/invoke` and `…/invoke/async`
- `reportUsage`, `reportProgress`, `reportCompletion` — usage service (**report** body uses ISO `timestamp`; `X-Tollara-Timestamp` = epoch **seconds** for HMAC)
- `getRequestStatus`, `getRequestResult` — async job polling

## Examples

### Verify HMAC (backend)

```ts
import { verifySignatureFromHeaders, getUserContext } from '@tollara/service-sdk';

const serviceSecret = 'your-service-shared-secret';
const valid = verifySignatureFromHeaders(serviceSecret, req.headers, rawBodyString);
if (valid) {
  const ctx = getUserContext(req.headers);
}
```

### Validate service key (caller)

```ts
import { validateServiceKey } from '@tollara/service-sdk';

const result = await validateServiceKey({
  serviceKey: 'bearer-token',
  serviceId: 'service-id',
  serviceSecret: 'service-secret',
});
```

Optional `baseUrl` when not using the default production origin. Successful validate results include **`serviceKeyId`** when Core returns it (§2.1).

### Usage estimate (caller)

Same trust model as validate: JSON body with the service key (no separate bearer on Core). Response HMAC is verified for success and typical denial statuses when signature headers are present.

```ts
import { estimateUsage } from '@tollara/service-sdk';

const est = await estimateUsage({
  serviceKey: 'bearer-token',
  serviceId: 'service-id',
  serviceSecret: 'service-secret',
  estimatedUnits: 1,
});
```

### Report usage

```ts
import { reportUsage } from '@tollara/service-sdk';

await reportUsage({
  userId: 'u1',
  serviceId: 'a1',
  unitsUsed: 1,
  serviceSecret: 'secret',
});
```

### Progress and completion (async)

URLs come from the platform (`progress_url`, `callback_url`).

```ts
import { CompletionStatus, reportProgress, reportCompletion } from '@tollara/service-sdk';

const progress = await reportProgress({
  progressUrl,
  requestId,
  stage: 'processing',
  percentageComplete: 50,
  serviceSecret,
});
if (!progress.success) {
  console.error(progress.httpStatus, progress.responseBody);
}

const complete = await reportCompletion({
  callbackUrl,
  requestId,
  status: CompletionStatus.Completed,
  result: 'done',
  serviceSecret,
  units: 1,
});
```

### Job status / result (caller)

```ts
import { getRequestStatus, getRequestResult } from '@tollara/service-sdk';

const st = await getRequestStatus({ requestId, serviceKey });
const res = await getRequestResult({ requestId, serviceKey });
```

Optional `baseUrl` on each call when not using the default origin.

## Build & test

```bash
npm ci
npm run build
npm test
```

## Release (npm)

Package name: **`@tollara/service-sdk`** ([npm scoped packages](https://docs.npmjs.com/about-scopes-and-packages)).

1. **Version** — Bump `"version"` in [`package.json`](package.json) (SemVer). npm will not let you publish the same version twice.
2. **Verify** — `npm ci`, `npm test`, and `npm run build` (or rely on `prepublishOnly`, which runs `build` on `npm publish`).
3. **Login** — `npm login` on the machine that will publish, or use an **automation token** / `NPM_TOKEN` in CI (see [access tokens](https://docs.npmjs.com/about-access-tokens) and [CI workflows](https://docs.npmjs.com/using-private-packages-in-a-ci-cd-workflow)).
4. **Publish** — From `sdk-js`:

   ```bash
   npm publish --access public
   ```

   The first publish of a **scoped** package to the public registry must use `--access public` (subsequent publishes can omit it if the package is already public).

5. **Tag** — Tag the Git commit that matches the published version.

Optional: `npm publish --dry-run` to inspect the tarball without uploading. `repository`, `files` (`dist`, `README.md`), and `prepublishOnly` are already set in `package.json`.

Contract reference: this README and the package API surface.
