# AgentVend SDK (Java)

Client SDK for AgentVend: verify HMAC on incoming gateway requests, validate agent keys, report usage, progress/completion, and poll async job status on the gateway.

**Package:** `com.agentvend:agent-sdk`

Dependencies are **Jackson**, **SLF4J**, and the **JDK** `java.net.http.HttpClient` (no Spring).

## Configuration (base URLs)

The SDK **does not hardcode** production hosts. You supply:

| Purpose | What you pass | Notes |
|--------|----------------|-------|
| **Core** (validate key) | `coreServiceUrl` | Usually includes your core path prefix, e.g. `https://core.example.com/api/v1`. Client calls `{coreServiceUrl}/agent-keys/validate`. |
| **Usage** (report) | `usageServiceUrl` | Host/base only, e.g. `https://usage.example.com`. Client posts to `{usageServiceUrl}/api/usage/report`. For ECS-style paths, align with [sdk-api-spec.md](../docs/sdk-api-spec.md) §3. |
| **Gateway** (poll status/result) | `gatewayBaseUrl` + `gatewayPathPrefix` | e.g. base `https://gw.example.com`, prefix `/api` (default) or `/gateway/api/v1` (ECS). |
| **Progress / completion** | Full URLs from async response | Use the exact `progressUrl` / `callbackUrl` strings (including query params) returned by the gateway. |

See [sdk-api-spec.md](../docs/sdk-api-spec.md) and [api-overview.md](../docs/api-overview.md).

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

## Examples

### Verify inbound HMAC (agent backend)

Pass your framework’s header accessor and the **raw UTF-8 body** the gateway signed (same bytes as in the canonical string). The SDK reads all `X-AgentVend-*` headers using the canonical names from `AgentVendHeaders`, and falls back to lowercase names when needed.

```java
import com.agentvend.client.AgentVendRequestVerifier;
import jakarta.servlet.http.HttpServletRequest; // or your stack’s request type

AgentVendRequestVerifier verifier = new AgentVendRequestVerifier(agentSecret);
String rawBody = rawRequestBodyUtf8;

boolean valid = verifier.verifyInboundHmac(request::getHeader, rawBody);
if (valid) {
    AgentVendRequestVerifier.UserContext ctx = verifier.userContextFromHeaders(request::getHeader);
}
```

You can also pass a `Map<String, String>` (`verifyInboundHmac(map, rawBody)`); keys are matched case-insensitively. For full control, build `InboundHmacRequest` with `SignedUserContext` and call `verifyInboundHmac(InboundHmacRequest)`.

### Validate agent key (caller)

```java
import com.agentvend.client.AgentKeyValidationClient;

import java.net.http.HttpClient;

AgentKeyValidationClient client = new AgentKeyValidationClient(
    "https://core.example.com/api/v1", "agent-id", "agent-secret", HttpClient.newHttpClient());
AgentKeyValidationClient.AgentKeyValidationResult result = client.validateAgentKey("bearer-token");
if (result != null) {
    // result.getUserId(), result.getPlan(), ...
}
```

### Report usage (backend)

```java
import com.agentvend.client.UsageServiceClient;
import com.agentvend.client.model.UsageReportResponse;

import java.net.http.HttpClient;

UsageServiceClient usage = new UsageServiceClient("https://usage.example.com", agentSecret, HttpClient.newHttpClient());
UsageReportResponse resp = usage.reportUsage(userId, agentId, BigDecimal.ONE);
```

### Report progress and completion (async backend)

Use the **full** URLs from the async job payload/response (must include `timestamp` query param where required).

```java
import com.agentvend.client.model.CompletionStatus;

boolean progressOk = usage.sendProgressUpdate(progressUrl, requestId, "processing", 50);
boolean doneOk = usage.sendCompletion(callbackUrl, requestId, CompletionStatus.COMPLETED, "result text", BigDecimal.ONE);
```

### Poll job status / result (caller, gateway)

```java
import com.agentvend.client.GatewayClient;
import com.agentvend.client.GatewayHttpResponse;

import java.net.http.HttpClient;

GatewayClient gw = new GatewayClient(HttpClient.newHttpClient());
GatewayHttpResponse status = gw.getRequestStatus(
    "https://gateway.example.com", "/api", requestId, agentKey);
GatewayHttpResponse result = gw.getRequestResult(
    "https://gateway.example.com", "/api", requestId, agentKey);
```

See [HMAC spec](../docs/hmac-spec.md) and [API spec](../docs/sdk-api-spec.md).
