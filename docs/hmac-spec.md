# HMAC specification (cross-language)

All SDKs must implement the same HMAC behavior so verification matches the Tollara gateway and usage service.

## Algorithm

- **Algorithm:** HMAC-SHA256
- **Key and message:** UTF-8 encoded
- **Output:** Base64-encoded

## Inbound request (gateway → agent backend)

The gateway sends requests to agent backends with signed headers. The agent must verify the signature.

**Canonical string to sign:** `payload + timestamp + userContextString` (concatenation, no separators).

- **payload**: Raw request body as string. Use empty string if there is no body. If the platform serializes JSON, use the same byte-for-byte string (e.g. normalized JSON).
- **timestamp**: Same value as `X-Tollara-Timestamp` (numeric string).
- **userContextString** (exact order, no extra separators):
  1. `userId` or `""`
  2. `plan` or `""`
  3. Comma-joined roles (omit the comma list entirely when there are no roles — i.e. contribute `""`)
  4. `quotaRemaining` string: if the quota header/value is present, append its canonical decimal string (e.g. `10`, `50.5`; match your language’s agreement with the gateway — typically no unnecessary trailing zeros). If absent/null, append `""`.
  5. `subscriptionActive` as exactly `"true"` or `"false"` (from `X-Tollara-Subscription-Active`; treat missing header as inactive → `"false"`).
  6. `billingModelType` or `""`
  7. `measurementType` or `""`
  8. `unitLabel` or `""`

**Signature:** `Base64(HMAC-SHA256(canonicalString, agentSecret))`.

Compare the computed signature with `X-Tollara-Signature` using **constant-time comparison** to avoid timing attacks.

## Outbound (agent → usage service: report / progress / complete)

When the agent calls the usage service (report, progress, completion), it must sign the request.

**Canonical string:** `bodyString + timestamp`

- **bodyString**: JSON string of the request body (same serialization used in the HTTP body).
- **timestamp**: Same value as the `X-Tollara-Timestamp` header (string).

**Signature:** `Base64(HMAC-SHA256(canonicalString, agentSecret))`.

Set headers: `X-Tollara-Signature`, `X-Tollara-Timestamp`.

## Validation response (core → client)

When the client calls the core service to validate an agent key, the response is signed.

**Verification:** Response body (raw JSON string) + timestamp (from response header `X-Tollara-Timestamp`) concatenated; compute `HMAC(responseBody + timestamp, agentSecret)`; compare to `X-Tollara-Signature` using constant-time comparison.

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

### Inbound style (extended userContextString)

**Vector A — subscriber with roles, quota, active subscription, no billing strings:**

- **payload:** `""`
- **timestamp:** `1700000000`
- **userContextString:** `user1` + `plan1` + `role1,role2` + `10` + `false` + `` + `` + `` = `user1plan1role1,role210false`
- **canonical:** `1700000000user1plan1role1,role210false`
- **key:** `my-agent-secret`

Compute `HMAC-SHA256(canonical, key)`, Base64; each SDK’s verifier should accept this when headers/context match.

**Vector B — owner-like (empty roles, large quota, subscription true):**

- **payload:** `{"hello":1}` (exact string)
- **timestamp:** `1700000000`
- **userContextString:** `user-1` + `owner` + `` + `9223372036854775807` + `true` + `` + `` + ``
- **key:** `test-agent-secret`

**Vector C — billing headers set:**

- **payload:** `""`
- **timestamp:** `1710000000`
- **userContextString:** `sub-user` + `basic` + `roleA,roleB` + `50` + `true` + `SUBSCRIPTION` + `PER_REQUEST` + `request`
- **key:** `test-agent-secret`

---

## Older canonical form (deprecated)

An earlier draft used `userContextString` = `userId + plan + roles + quota` only (no subscription or billing suffix). The **current** spec is the extended string above; gateways and agents must agree on `X-Tollara-Subscription-Active` and the optional billing headers. See [sdk-api-spec.md](sdk-api-spec.md) §4.
