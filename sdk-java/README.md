# AgentVend SDK (Java)

Client SDK for AgentVend: verify HMAC on incoming gateway requests, validate **service keys**, **usage pre-flight** (service-key and JWT estimates), **gateway invoke** (sync/async), report usage, progress/completion, and poll async job status on the gateway.

**Package:** `com.agentvend:service-sdk`

Dependencies are **Jackson**, **SLF4J**, and the **JDK** `java.net.http.HttpClient` (no Spring).

## Configuration

### Recommended: `AgentVendClient`

Use **`AgentVendClient`** with one API origin.

| Setting | Default | Notes |
|--------|---------|--------|
| API origin | **`https://api.agentvend.api`** (`AgentVendClient.DEFAULT_API_URL`) | Override with `Builder.apiUrl(...)`, or env **`AGENTVEND_API_URL`** for staging/tests — no trailing slash required |
| Service ID | From env **`AGENTVEND_SERVICE_ID`**, or `Builder.serviceId(...)` | Optional if Core can infer the service from the key |
| Service secret | From env **`AGENTVEND_SERVICE_SECRET`**, or `Builder.serviceSecret(...)` | **Required** (Usage HMAC + Core response verification) |

**Progress / completion** still use the **full** `progressUrl` / `callbackUrl` strings from the gateway (including query params).

**Usage report signing (Usage §3):** the JSON body includes an ISO-8601 **`timestamp`** field; **`X-AgentVend-Timestamp`** is **Unix epoch seconds** (same value concatenated to the raw body string for HMAC). Progress/completion use the **timestamp from the URL** query string for signing, as returned by the platform.

### Environment variables

Builder values win when both are set; otherwise the SDK reads:

| Variable | Purpose |
|----------|---------|
| **`AGENTVEND_API_URL`** | Optional. Overrides the default production API origin when set. |
| **`AGENTVEND_SERVICE_ID`** | Service UUID if you omit `serviceId(...)` (optional) |
| **`AGENTVEND_SERVICE_SECRET`** | Service shared secret if you omit `serviceSecret(...)` (**required** one way or the other) |

In code, names are also available as `AgentVendClient.ENV_API_URL`, `ENV_SERVICE_ID`, and `ENV_SERVICE_SECRET`. The default base URL is `AgentVendClient.DEFAULT_API_URL`.

## Install

Use the same version as `version` in [`build.gradle`](build.gradle) (below matches the repo as of this README).

**Gradle:**

```kotlin
implementation("com.agentvend:service-sdk:0.0.5")
```

**Maven:**

```xml
<dependency>
  <groupId>com.agentvend</groupId>
  <artifactId>service-sdk</artifactId>
  <version>0.0.5</version>
</dependency>
```

## Build

From this directory:

```bash
./gradlew build
```

## Publish to Maven Central (Sonatype Central Publisher)

**Full background, Central requirements, and Gradle property reference:** [`docs/maven-central-java-sdk-publishing.md`](../docs/maven-central-java-sdk-publishing.md).

**Release checklist (short):**

1. Bump `version` in `build.gradle` to a **non-snapshot** release (Maven Central rejects `-SNAPSHOT` for this flow).
2. Run `./gradlew test` and fix failures.
3. Commit; optionally tag `v<version>` in Git **after** the artifacts are accepted.
4. Follow steps 1–6 below (signing, credentials, `./gradlew finalizeSonatypeCentralUpload`, Portal release).
5. Update consumer docs (this README’s **Install** section) to the new version when you cut a release.

Namespace **`com.agentvend`** must stay verified in [Maven Central Portal](https://central.sonatype.com/). This project uses Gradle **`maven-publish`** plus **`signing`**, uploads to Sonatype’s [OSSRH Staging API compatibility endpoint](https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/), then runs a **finalize** POST so the deployment appears under [Publishing](https://central.sonatype.com/publishing).

### 1. One-time: GPG key for signing

Maven Central requires a **GPG signature** (`.asc`) for each published file. Follow [Sonatype’s GPG guide](https://central.sonatype.org/publish/requirements/gpg/): create a key, protect it with a passphrase, and **publish the public key** to a supported keyserver (for example `keyserver.ubuntu.com`).

### 2. One-time: Portal user token

In the Portal, [generate a user token](https://central.sonatype.org/publish/generate-portal-token/) (username + password). You will use these as Maven credentials (not your login password).

### 3. Configure secrets (do not commit)

Prefer **`%USERPROFILE%\.gradle\gradle.properties`** (Windows) or **`~/.gradle/gradle.properties`** with:

```properties
mavenCentralUsername=<token username>
mavenCentralPassword=<token password>

# POM developer (override the build defaults)
mavenCentralDeveloperName=Your Name
mavenCentralDeveloperEmail=you@example.com
```

**CI:** set environment variables instead: `MAVEN_CENTRAL_PUBLISH_USERNAME`, `MAVEN_CENTRAL_PUBLISH_PASSWORD`, and for in-memory signing `SIGNING_KEY` (armored private key) and `SIGNING_PASSWORD`. Optional: `signingKey` / `signingPassword` Gradle properties.

**Local signing with GPG:** if you do not use `SIGNING_KEY`, configure the [Gradle signing plugin](https://docs.gradle.org/current/userguide/signing_plugin.html) via `signing.keyId`, `signing.password`, and a secret key export as in the Gradle docs.

### 4. Release version

[Maven Central does not accept](https://central.sonatype.org/publish/requirements/) versions ending in `-SNAPSHOT` for the main repository. Before a release, set `version` in `build.gradle` to a proper release (for example `1.0.0`), tag in Git, then publish.

### 5. Upload and finalize

From `sdk-java`:

```bash
./gradlew finalizeSonatypeCentralUpload
```

That runs **`publishMavenJavaPublicationToOssrhStagingApiRepository`** (signed artifacts + checksums) and then **`finalizeSonatypeCentralUpload`** (POST to Sonatype so the bundle shows in the Portal). The finalize step **must use the same public IP** as the upload (run both in one CI job or one local session).

Optional Gradle properties: `mavenCentralNamespace` (default `com.agentvend`), `mavenCentralPublishingType` (`user_managed` vs `automatic` — see Sonatype docs), `sonatype.manualUploadPath` / `sonatype.stagingApiHost` if Sonatype changes URLs (confirm against their OpenAPI if needed).

### 6. Release in the Portal

Open [central.sonatype.com/publishing](https://central.sonatype.com/publishing): validate the deployment, then publish to Maven Central (or use `publishing_type=automatic` on the manual endpoint if you automate that).

**POM note:** the build declares **Apache License 2.0** and SCM pointing at this repo. If your license differs, update the `pom { licenses { ... } }` block in `build.gradle` and add a matching `LICENSE` file at the repository root.

## Examples

### Verify inbound HMAC (service backend)

Pass your framework’s header accessor and the **raw UTF-8 body** the gateway signed (same bytes as in the canonical string). The SDK reads all `X-AgentVend-*` headers using the canonical names from `AgentVendHeaders`, and falls back to lowercase names when needed. Verification defaults to HMAC user-context **v2** (leading `"2"`, no quota segment).

```java
import com.agentvend.client.AgentVendRequestVerifier;
import jakarta.servlet.http.HttpServletRequest; // or your stack’s request type

import java.util.Optional;

// Create a verifier for this service’s shared secret (used to validate gateway HMACs).
AgentVendRequestVerifier verifier = new AgentVendRequestVerifier(serviceSecret);
String rawBody = rawRequestBodyUtf8;

// Preferred: verify and read user context in one step (empty Optional if the HMAC is invalid).
Optional<AgentVendRequestVerifier.UserContext> verified =
        verifier.verifyInboundHmacAndGetUserContext(request::getHeader, rawBody);
verified.ifPresent(ctx -> {
    // ctx.getUserId(), ctx.getPlan(), …
});

// Or verify and read separately:
boolean valid = verifier.verifyInboundHmac(request::getHeader, rawBody);
if (valid) {
    AgentVendRequestVerifier.UserContext ctx = verifier.userContextFromHeaders(request::getHeader);
}
```

You can also pass a `Map<String, String>` (`verifyInboundHmac(map, rawBody)` and `verifyInboundHmacAndGetUserContext(map, rawBody)`); keys are matched case-insensitively. For full control, build `InboundHmacRequest` with `SignedUserContext` and call `verifyInboundHmac(InboundHmacRequest)`.

### Caller / backend HTTP APIs (single client)

```java
import com.agentvend.client.AgentVendClient;
import com.agentvend.client.GatewayHttpResponse;
import com.agentvend.client.GatewayInvokeResult;
import com.agentvend.client.model.CompletionStatus;
import com.agentvend.client.model.UsageEstimateResult;
import com.agentvend.client.model.UsageReportResponse;

import java.math.BigDecimal;
import java.net.http.HttpClient;

// Default API origin is production; set .apiUrl(...) or AGENTVEND_API_URL only to override.
// .serviceSecret(...) (or AGENTVEND_SERVICE_SECRET) is required.
AgentVendClient client = AgentVendClient.builder()
    .serviceId(serviceId)
    // Shared secret: signs outbound Usage calls and verifies Core validate responses (required).
    .serviceSecret(serviceSecret)
    .build();

// Validate a caller’s service key; verify response HMAC inside the client.
var validation = client.validateServiceKey("bearer-token");
// validation.getServiceKeyId() — Core key row id when present (validate success, §2.1).

// Pre-flight: Core POST /service-keys/estimate-usage (same body trust as validate; no Bearer).
// Verifies response HMAC on 200 / 403 / 429 when signature headers are present.
UsageEstimateResult estimate = client.estimateUsage("bearer-token", new BigDecimal("1"));
if (estimate != null) {
    boolean allowed = estimate.isWouldAllow(); // would AgentVend allow the task to complete based on available usage limits?
    int status = estimate.getHttpStatus();
    // estimate.getSufficientCredits(), getWouldExceedCap(), getBreakdown(), …
}

// JWT usage estimate (Core POST …/billing/usage/estimate) — unsigned response; pass a Bearer JWT.
UsageEstimateResult jwtEst = client.estimateUsageWithJwt("jwt-here", internalUserId, serviceId, new BigDecimal("1"));

// Gateway invoke (sync or async); Bearer uses the service API key string.
GatewayInvokeResult inv = client.invokeService("POST", serviceId, endpointId, serviceKey, "{}", false);
// inv.getAsyncEnvelope() on HTTP 202: requestId, callbackUrl, progressUrl

// Record billed units for a user/service (signed with service secret; body timestamp ISO, header epoch seconds).
UsageReportResponse usageResp = client.reportUsage(userId, serviceId, BigDecimal.ONE);

// Give progress update (use the full progressUrl from the gateway/async payload).
client.sendProgressUpdate(progressUrl, requestId, "some processing info", 50);

// Job finished (use the full callbackUrl from the gateway/async payload).
client.sendCompletion(callbackUrl, requestId, CompletionStatus.COMPLETED, "some result", java.math.BigDecimal.ONE);

// Poll async job status (Bearer service key).
GatewayHttpResponse status = client.getRequestStatus(requestId, serviceKey);

```

See this README for the public SDK contract and usage examples.
