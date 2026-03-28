# AgentVend SDK (Java)

Client SDK for AgentVend: verify HMAC on incoming gateway requests, validate agent keys, report usage, progress/completion, and poll async job status on the gateway.

**Package:** `com.agentvend:agent-sdk`

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

### Verify HMAC and user context (agent backend)

Use `org.springframework.http.HttpHeaders` (case-insensitive). Pass the **raw body string** the gateway signed (read once or use a caching request wrapper if filters consume the stream).

```java
import com.agentvend.client.AgentVendHeaders;
import com.agentvend.client.AgentvendRequestVerifier;
import org.springframework.http.HttpHeaders;

String agentSecret = "your-agent-secret";
AgentvendRequestVerifier verifier = new AgentvendRequestVerifier(agentSecret);

HttpHeaders headers = new HttpHeaders();
headers.add(AgentVendHeaders.SIGNATURE, request.getHeader(AgentVendHeaders.SIGNATURE));
headers.add(AgentVendHeaders.TIMESTAMP, request.getHeader(AgentVendHeaders.TIMESTAMP));
// ... copy other X-AgentVend-* into headers, or use an adapter from your framework

String rawBody = requestBodyString;

boolean valid = verifier.verifyInboundHmac(headers, rawBody);
if (valid) {
    AgentvendRequestVerifier.UserContext ctx = verifier.userContextFromHeaders(headers);
}
```

**Low-level DTO** (tests or non-Spring): `InboundHmacRequest` + `SignedUserContext` with `verifyInboundHmac(InboundHmacRequest)`, or `verifyInboundHmac(Map<String, String> headers, String payload)` with case-insensitive keys.

### Validate agent key (caller)

```java
import com.agentvend.client.AgentKeyValidationClient;
import org.springframework.web.client.RestTemplate;

RestTemplate rest = new RestTemplate();
AgentKeyValidationClient client = new AgentKeyValidationClient(
    "https://core.example.com/api/v1", "agent-id", "agent-secret", rest);
AgentKeyValidationClient.AgentKeyValidationResult result = client.validateAgentKey("bearer-token");
if (result != null) {
    // result.getUserId(), result.getPlan(), ...
}
```

### Report usage (backend)

```java
import com.agentvend.client.UsageServiceClient;
import com.agentvend.client.model.UsageReportResponse;

UsageServiceClient usage = new UsageServiceClient("https://usage.example.com", agentSecret, restTemplate);
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
import org.springframework.http.ResponseEntity;

GatewayClient gw = new GatewayClient(restTemplate);
ResponseEntity<String> status = gw.getRequestStatus(
    "https://gateway.example.com", "/api", requestId, agentKey);
ResponseEntity<String> result = gw.getRequestResult(
    "https://gateway.example.com", "/api", requestId, agentKey);
```

See [HMAC spec](../docs/hmac-spec.md) and [API spec](../docs/sdk-api-spec.md).
