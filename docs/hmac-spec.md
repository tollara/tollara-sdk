# HMAC specification (cross-language)

All SDKs must implement the same HMAC behavior so verification matches the AgentVend gateway and usage service.

## Algorithm

- **Algorithm:** HMAC-SHA256
- **Key and message:** UTF-8 encoded
- **Output:** Base64-encoded

## Inbound request (gateway → agent backend)

The gateway sends requests to agent backends with signed headers. The agent must verify the signature.

**Canonical string to sign:** `payload + timestamp + userContextString` (concatenation, no separators).

- **payload**: Raw request body as string. Use empty string if there is no body. If the platform serializes JSON, use the same byte-for-byte string (e.g. normalized JSON).
- **timestamp**: Same value as `X-AgentVend-Timestamp` (numeric string).
- **userContextString**: `userId ?? ""` + `plan ?? ""` + `roles.join(",")` + `quotaRemaining.toString()` (no separators between; nulls as empty string).

**Signature:** `Base64(HMAC-SHA256(canonicalString, agentSecret))`.

Compare the computed signature with `X-AgentVend-Signature` using **constant-time comparison** to avoid timing attacks.

## Outbound (agent → usage service: report / progress / complete)

When the agent calls the usage service (report, progress, completion), it must sign the request.

**Canonical string:** `bodyString + timestamp`

- **bodyString**: JSON string of the request body (same serialization used in the HTTP body).
- **timestamp**: Same value as the `X-AgentVend-Timestamp` header (string).

**Signature:** `Base64(HMAC-SHA256(canonicalString, agentSecret))`.

Set headers: `X-AgentVend-Signature`, `X-AgentVend-Timestamp`.

## Validation response (core → client)

When the client calls the core service to validate an agent key, the response is signed.

**Verification:** Response body (raw JSON string) + timestamp (from response header `X-AgentVend-Timestamp`) concatenated; compute `HMAC(responseBody + timestamp, agentSecret)`; compare to `X-AgentVend-Signature` using constant-time comparison.

## Replay protection

Implementations may enforce a timestamp window (e.g. ±5 minutes) to limit replay. The reference Java verifier does not enforce this by default; document the behavior in each SDK.

---

## Test vectors

Use these to validate HMAC implementation in any language.

### Outbound style (bodyString + timestamp)

**Inputs:**

- **data** (canonical string): `1234567890`
- **key** (secret): `secret`
- **Encoding:** UTF-8

**Expected signature (Base64):**

```
Bgs+chJF8gBA3xW2542Tm7B7l571zTPfLMBiCBwOp2c=
```

**Verification:** `HMAC-SHA256("1234567890", "secret")` then Base64-encode the result. Must equal the expected signature above.

### Inbound style (payload + timestamp + userContextString)

**Inputs:**

- **payload:** `` (empty string)
- **timestamp:** `1700000000`
- **userContextString:** `user1plan1role1,role210` (userId=user1, plan=plan1, roles=role1,role2, quotaRemaining=10)
- **canonical string:** `` + `1700000000` + `user1plan1role1,role210` = `1700000000user1plan1role1,role210`
- **key:** `my-agent-secret`

**Computation:** Implement `HMAC-SHA256(canonicalString, key)` with UTF-8, then Base64. Each language can verify by comparing against its own HMAC implementation; no precomputed value given here to avoid copy-paste errors — derive from the algorithm.

For a second vector with non-empty payload: **payload** = `{"foo":"bar"}`, **timestamp** = `1700000001`, **userContextString** = `` (all empty). **canonical string** = `{"foo":"bar"}1700000001`. **key** = `k`. Implementations should unit test with these inputs and assert the signature is consistent across runs.
