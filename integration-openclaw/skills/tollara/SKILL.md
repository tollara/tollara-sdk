# Tollara – Caller

When the user or the agent needs to **call an agent hosted on Tollara**, use the **tollara_call_agent** tool.

## When to use

- User asks to invoke an external agent, run an Tollara agent, or call an API that is exposed as an Tollara agent.
- You need to run a specific agent by `agentId` and `endpointId` (or the user provides a key that maps to an agent).

## Tool: tollara_call_agent

- **agentId** (required): The agent ID on Tollara.
- **endpointId** (required): The endpoint ID to invoke.
- **body** (optional): JSON body for the request.
- **async** (optional): If true, returns `requestId`, `progressUrl`, `callbackUrl` for async flows.

## Errors

- **401 / invalid key**: Check that the plugin is configured with a valid `serviceKey`.
- **403 / quota exceeded**: The subscription has no remaining quota; inform the user.
- **5xx / gateway error**: Retry once or report that the service is temporarily unavailable.

## Example

User: "Run the summarizer agent on this text."

1. Resolve which agent and endpoint (e.g. from config or user).
2. Call `tollara_call_agent` with `agentId`, `endpointId`, and `body: { "text": "..." }`.
3. Return the tool result to the user.
