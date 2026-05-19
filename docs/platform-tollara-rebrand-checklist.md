# Platform Tollara rebrand checklist

Ship **in the same release train** as [tollara-sdk](https://github.com/tollara/tollara-sdk) (this repo).

## Gateway

- [ ] Emit **`X-Tollara-*`** on all signed proxied requests (full set per MAIN-SDK-API-SPEC §4).
- [ ] Route public API at **`https://api.tollara.ai`**.
- [ ] Remove production use of **`X-AgentVend-*`**.

## Core

- [ ] Validate-service-key and related signed responses use **`X-Tollara-Signature`** / **`X-Tollara-Timestamp`**.
- [ ] Javadoc discovery tag: **`TOLLARA-SDK-ENDPOINT`** (replace `AGENTVEND-SDK-ENDPOINT`).

## Usage

- [ ] Accept outbound SDK signing with **`X-Tollara-*`** on report, progress, and completion.

## Configuration

- [ ] ECS / task definitions and local dev: **`TOLLARA_API_URL`**, **`TOLLARA_SERVICE_ID`**, **`TOLLARA_SERVICE_SECRET`** (drop `AGENTVEND_*`).

## Verification

- [ ] Staging E2E: invoke, validate, async progress/complete, inbound HMAC verification with Tollara SDK **2.x** only.
