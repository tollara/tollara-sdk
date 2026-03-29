# AgentVend SDK (Java)

Client SDK for AgentVend: verify HMAC on incoming gateway requests, validate agent keys, report usage, progress/completion, and poll async job status on the gateway.

**Package:** `com.agentvend:agent-sdk`

Dependencies are **Jackson**, **SLF4J**, and the **JDK** `java.net.http.HttpClient` (no Spring).

## Configuration

### Recommended: single `AgentVendClient`

Use **`AgentVendClient`** with one API origin. The SDK appends the path prefixes from [sdk-api-spec.md](../docs/sdk-api-spec.md) (default deployment). Override prefixes when using ECS layouts or local Docker.

| Setting | Default | Notes |
|--------|---------|--------|
| API origin | **`https://api.agentvend.api`** (`AgentVendClient.DEFAULT_API_URL`) | Override with `Builder.apiUrl(...)`, or env **`AGENTVEND_API_URL`** for staging/tests — no trailing slash required |
| Agent ID | From env **`AGENTVEND_AGENT_ID`**, or `Builder.agentId(...)` | Optional if Core can infer the agent from the key |
| Agent secret | From env **`AGENTVEND_AGENT_SECRET`**, or `Builder.agentSecret(...)` | **Required** (Usage HMAC + Core response verification) |
| Core prefix | `/api/v1` | `Builder.corePathPrefix(...)` for `/core/api/v1` (ECS) |
| Gateway prefix | `/api` | `Builder.gatewayPathPrefix(...)` for `/gateway/api/v1` (ECS) |
| Usage prefix | `/api/usage` | `Builder.usagePathPrefix(...)` for `/usage/api/v1` (ECS) |

Split hosts (optional): `Builder.coreApiUrl(...)`, `gatewayApiUrl(...)`, `usageApiUrl(...)` each default to the main API URL when unset.

**Progress / completion** still use the **full** `progressUrl` / `callbackUrl` strings from the gateway (including query params).

### Environment variables

Builder values win when both are set; otherwise the SDK reads:

| Variable | Purpose |
|----------|---------|
| **`AGENTVEND_API_URL`** | Optional. Overrides the default production API origin when set. |
| **`AGENTVEND_AGENT_ID`** | Agent UUID if you omit `agentId(...)` (optional) |
| **`AGENTVEND_AGENT_SECRET`** | Agent secret if you omit `agentSecret(...)` (**required** one way or the other) |

In code, names are also available as `AgentVendClient.ENV_API_URL`, `ENV_AGENT_ID`, and `ENV_AGENT_SECRET`. The default base URL is `AgentVendClient.DEFAULT_API_URL`.

### Low-level clients

`AgentKeyValidationClient`, `UsageServiceClient`, and `GatewayClient` remain available if you need fully manual URL assembly.

## Install

**Gradle:**

```kotlin
implementation("com.agentvend:agent-sdk:1.0.0")
```

**Maven:**

```xml
<dependency>
  <groupId>com.agentvend</groupId>
  <artifactId>agent-sdk</artifactId>
  <version>1.0.0</version>
</dependency>
```

## Build

From this directory:

```bash
./gradlew build
```

## Publish to Maven Central (Sonatype Central Publisher)

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

### Verify inbound HMAC (agent backend)

Pass your framework’s header accessor and the **raw UTF-8 body** the gateway signed (same bytes as in the canonical string). The SDK reads all `X-AgentVend-*` headers using the canonical names from `AgentVendHeaders`, and falls back to lowercase names when needed.

```java
import com.agentvend.client.AgentVendRequestVerifier;
import jakarta.servlet.http.HttpServletRequest; // or your stack’s request type

import java.util.Optional;

// Create a verifier for this agent’s shared secret (used to validate gateway HMACs).
AgentVendRequestVerifier verifier = new AgentVendRequestVerifier(agentSecret);
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
import com.agentvend.client.AgentKeyValidationClient;
import com.agentvend.client.AgentVendClient;
import com.agentvend.client.GatewayHttpResponse;
import com.agentvend.client.model.CompletionStatus;
import com.agentvend.client.model.UsageReportResponse;

import java.math.BigDecimal;
import java.net.http.HttpClient;

// Default API origin is production; set .apiUrl(...) or AGENTVEND_API_URL only to override.
// .agentSecret(...) (or AGENTVEND_AGENT_SECRET) is required.
AgentVendClient client = AgentVendClient.builder()
    .agentId(agentId)
    // Shared secret: signs outbound Usage calls and verifies Core validate responses (required).
    .agentSecret(agentSecret)
    .build();

// Validate a caller’s agent key; verify response HMAC inside the client.
AgentKeyValidationClient.AgentKeyValidationResult validation =
        client.validateAgentKey("bearer-token");

// Record billed units for a user/agent (signed with agent secret).
UsageReportResponse usageResp = client.reportUsage(userId, agentId, BigDecimal.ONE);

// Give progress update (use the full progressUrl from the gateway/async payload).
client.sendProgressUpdate(progressUrl, requestId, "some processing info", 50);

// Job finished (use the full callbackUrl from the gateway/async payload).
client.sendCompletion(callbackUrl, requestId, CompletionStatus.COMPLETED, "some result", java.math.BigDecimal.ONE);

// Poll async job status (Bearer agent key).
GatewayHttpResponse status = client.getRequestStatus(requestId, agentKey);

```

See [HMAC spec](../docs/hmac-spec.md) and [API spec](../docs/sdk-api-spec.md).
