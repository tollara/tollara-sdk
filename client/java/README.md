# Marketplace Client Library (Java)

Reusable Java library for agent verification and marketplace integration.

## Features

- **HMAC Signature Verification**: Verify marketplace request signatures
- **Agent Key Validation**: Validate agent-scoped API keys via core-service
- **User Context Extraction**: Extract user context from X-Marketplace-* headers
- **Usage Service Integration**: Send progress updates and completion notifications

## Usage

### Adding to Your Project

Add the dependency to your `build.gradle`:

```gradle
dependencies {
    if (findProject(':client:java') != null) {
        implementation project(':client:java')
    } else {
        implementation 'com.bugisiw.marketplace:marketplace-client-java'
    }
}
```

### Example: Verifying Requests

```java
import com.bugisiw.marketplace.client.MarketplaceRequestVerifier;
import jakarta.servlet.http.HttpServletRequest;

// Initialize
String agentSecret = "your-agent-secret";
MarketplaceRequestVerifier verifier = new MarketplaceRequestVerifier(agentSecret);

// Extract headers from request
String signature = request.getHeader("X-Marketplace-Signature");
String timestamp = request.getHeader("X-Marketplace-Timestamp");
String userId = request.getHeader("X-Marketplace-User-ID");
String plan = request.getHeader("X-Marketplace-Plan");
String rolesHeader = request.getHeader("X-Marketplace-Roles");
String quotaHeader = request.getHeader("X-Marketplace-Quota-Remaining");

// Parse roles and quota
List<String> roles = rolesHeader != null ? 
    Arrays.asList(rolesHeader.split(",")) : Collections.emptyList();
BigDecimal quotaRemaining = quotaHeader != null ? 
    new BigDecimal(quotaHeader) : BigDecimal.ZERO;

// Get request body
String payload = getRequestBody(request); // Your method to get body

// Verify HMAC signature (includes user context)
boolean isValid = verifier.verifyHmacSignature(
    signature, timestamp, payload, userId, plan, roles, quotaRemaining
);

if (isValid) {
    // Extract user context
    UserContext userContext = verifier.extractUserContext(request);
    String userId = userContext.getUserId();
    List<String> roles = userContext.getRoles();
    String plan = userContext.getPlan();
    BigDecimal quotaRemaining = userContext.getQuotaRemaining();
}
```

### Example: Agent Key Validation

```java
import com.bugisiw.marketplace.client.AgentKeyValidationClient;
import org.springframework.web.client.RestTemplate;

// Initialize
String coreServiceUrl = "http://core-service:8081/api/v1";
String agentId = "your-agent-id";
String agentSecret = "your-agent-secret";
RestTemplate restTemplate = new RestTemplate();

AgentKeyValidationClient client = new AgentKeyValidationClient(
    coreServiceUrl, agentId, agentSecret, restTemplate
);

// Validate agent key (for async re-validation)
String agentKey = request.getHeader("Authorization").replace("Bearer ", "");
AgentKeyValidationResult result = client.validateAgentKey(agentKey);

if (result != null && result.isValid()) {
    String userId = result.getUserId();
    String agentId = result.getAgentId();
    String plan = result.getPlan();
    List<String> roles = result.getRoles();
    BigDecimal quotaRemaining = result.getQuotaRemaining();
    boolean subscriptionActive = result.isSubscriptionActive();
}
```

### Example: Usage Service Integration

```java
import com.bugisiw.marketplace.client.UsageServiceClient;

String usageServiceUrl = "http://usage-service:8084";
UsageServiceClient client = new UsageServiceClient(
    usageServiceUrl, agentSecret, restTemplate
);

// Send progress update
client.sendProgressUpdate(requestId, "Processing", 50, null);

// Send completion
client.sendCompletion(requestId, "COMPLETED", "Result data", 
    null, "text/plain", BigDecimal.ONE);
```

## Components

- `MarketplaceRequestVerifier`: Verifies HMAC signatures and extracts user context from headers
- `AgentKeyValidationClient`: Validates agent-scoped API keys via core-service validation endpoint
- `UsageServiceClient`: Sends progress updates and completion notifications to usage service

## Request Headers

All marketplace requests include the following headers:
- `X-Marketplace-Signature`: HMAC signature of the request
- `X-Marketplace-Timestamp`: Unix timestamp
- `X-Marketplace-User-ID`: User ID
- `X-Marketplace-Plan`: Subscription plan tier
- `X-Marketplace-Roles`: Comma-separated list of user roles
- `X-Marketplace-Quota-Remaining`: Remaining quota/credits
- `X-Marketplace-Subscription-Active`: Whether subscription is active

## Dependencies

- Spring Web (for RestTemplate)
- Jackson (for JSON processing)
- Common Utils (for HMAC utilities)

