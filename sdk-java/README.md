# Agent Hub SDK (Java)

Client SDK for Agent Hub: verify HMAC on incoming gateway requests, validate agent keys, report usage, and send progress/completion for async flows.

**Package (placeholder):** `com.marketplace:agent-sdk`

## Install

**Gradle:**

```kotlin
implementation("com.marketplace:agent-sdk:1.0.0")
```

**Maven:**

```xml
<dependency>
  <groupId>com.marketplace</groupId>
  <artifactId>agent-sdk</artifactId>
  <version>1.0.0</version>
</dependency>
```

## Build

From this directory:

```bash
./gradlew build
```

(If the Gradle wrapper is missing, run `gradle wrapper` first, or use your system Gradle.)

## Minimal example

### Verify HMAC and get user context (backend)

```java
import com.bugisiw.marketplace.client.MarketplaceRequestVerifier;

String agentSecret = "your-agent-secret";
MarketplaceRequestVerifier verifier = new MarketplaceRequestVerifier(agentSecret);

// From your HTTP request:
String signature = request.getHeader("X-Marketplace-Signature");
String timestamp = request.getHeader("X-Marketplace-Timestamp");
String payload = requestBody; // raw body string
String userId = request.getHeader("X-Marketplace-User-ID");
String plan = request.getHeader("X-Marketplace-Plan");
List<String> roles = Arrays.asList(request.getHeader("X-Marketplace-Roles").split(","));
BigDecimal quotaRemaining = new BigDecimal(request.getHeader("X-Marketplace-Quota-Remaining"));

boolean valid = verifier.verifyHmacSignature(signature, timestamp, payload, userId, plan, roles, quotaRemaining);
if (valid) {
    MarketplaceRequestVerifier.UserContext ctx = verifier.extractUserContext(userId, plan, ...);
    // use ctx.getUserId(), ctx.getPlan(), etc.
}
```

### Validate agent key (caller)

```java
import com.bugisiw.marketplace.client.AgentKeyValidationClient;
import org.springframework.web.client.RestTemplate;

RestTemplate rest = new RestTemplate();
AgentKeyValidationClient client = new AgentKeyValidationClient(
    "https://core.example.com/api/v1", "agent-id", "agent-secret", rest);
AgentKeyValidationClient.AgentKeyValidationResult result = client.validateAgentKey("bearer-token");
if (result != null) {
    // result.getUserId(), result.getPlan(), result.getQuotaRemaining(), etc.
}
```

### Report usage (backend)

```java
import com.bugisiw.marketplace.client.UsageServiceClient;
import com.bugisiw.marketplace.client.model.UsageReportResponse;

UsageServiceClient usage = new UsageServiceClient("https://usage.example.com", agentSecret, restTemplate);
UsageReportResponse resp = usage.reportUsage(userId, agentId, BigDecimal.ONE, null);
```

See [HMAC spec](../docs/hmac-spec.md) and [API overview](../docs/api-overview.md) in the repo root `docs/`.
