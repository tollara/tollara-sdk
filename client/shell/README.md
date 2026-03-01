# Shell Scripts

This folder contains PowerShell scripts for interacting with the Agent Hub services from the command line.

## invoke-test-agent.ps1

Invokes a Test Agent endpoint via the Gateway Service. Supports both synchronous and asynchronous invocations.

### Prerequisites

- PowerShell 5.1 or later
- Gateway Service running and accessible
- Valid agent key for the agent you want to invoke

### Basic Usage

#### Synchronous Invocation

```powershell
.\invoke-test-agent.ps1 `
    -AgentId "your-agent-id" `
    -EndpointId "your-endpoint-id" `
    -AgentKey "agk_live_your-agent-key-here" `
    -Method POST `
    -Body '{"message":"Hello World"}'
```

#### Asynchronous Invocation

```powershell
.\invoke-test-agent.ps1 `
    -AgentId "your-agent-id" `
    -EndpointId "your-endpoint-id" `
    -AgentKey "agk_live_your-agent-key-here" `
    -Method POST `
    -Body '{"message":"Hello World"}' `
    -Async
```

#### GET Request with Query Parameters

```powershell
.\invoke-test-agent.ps1 `
    -AgentId "your-agent-id" `
    -EndpointId "your-endpoint-id" `
    -AgentKey "agk_live_your-agent-key-here" `
    -Method GET `
    -QueryParams @{param1='value1'; param2='value2'}
```

#### Using a JSON File for Body

```powershell
.\invoke-test-agent.ps1 `
    -AgentId "your-agent-id" `
    -EndpointId "your-endpoint-id" `
    -AgentKey "agk_live_your-agent-key-here" `
    -Method POST `
    -Body "C:\path\to\request-body.json"
```

### Parameters

- **AgentId** (required, unless using `-RunTestAgent`): The ID of the agent to invoke
- **EndpointId** (required, unless using `-RunTestAgent`): The ID of the endpoint to invoke
- **AgentKey** (required): The agent key for authentication (can include or omit "Bearer " prefix)
- **Method** (optional): HTTP method (GET, POST, PUT, DELETE). Default: POST
- **Body** (optional): JSON body as string or path to JSON file
- **QueryParams** (optional): Hashtable of query parameters
- **Async** (optional): Switch to invoke asynchronously
- **RunTestAgent** (optional): Automatically find and use the Test Agent. Queries the core service to find the agent by name "Test Agent", gets its endpoints, and uses the first active endpoint.
- **GatewayUrl** (optional): Base URL of Gateway Service. Default: http://localhost:8083/api
- **CoreServiceUrl** (optional): Base URL of Core Service for querying agent information. Default: http://localhost:8081/api/v1
- **PollStatus** (optional): For async requests, poll for status. Default: $true
- **PollInterval** (optional): Seconds between status polls. Default: 2

### Examples

#### Example 1: Simple POST Request

```powershell
.\invoke-test-agent.ps1 `
    -AgentId "34298d8b-8d8a-4e93-8877-0a0787f71640" `
    -EndpointId "endpoint-123" `
    -AgentKey "agk_live_abc123def456..." `
    -Body '{"input":"test data"}'
```

#### Example 2: Async Request with Custom Polling

```powershell
.\invoke-test-agent.ps1 `
    -AgentId "34298d8b-8d8a-4e93-8877-0a0787f71640" `
    -EndpointId "endpoint-123" `
    -AgentKey "agk_live_abc123def456..." `
    -Method POST `
    -Body '{"input":"test data"}' `
    -Async `
    -PollInterval 5
```

#### Example 3: GET Request with Query Parameters

```powershell
.\invoke-test-agent.ps1 `
    -AgentId "34298d8b-8d8a-4e93-8877-0a0787f71640" `
    -EndpointId "endpoint-123" `
    -AgentKey "agk_live_abc123def456..." `
    -Method GET `
    -QueryParams @{search='test'; limit='10'}
```

#### Example 4: Using Different Gateway URL

```powershell
.\invoke-test-agent.ps1 `
    -AgentId "34298d8b-8d8a-4e93-8877-0a0787f71640" `
    -EndpointId "endpoint-123" `
    -AgentKey "agk_live_abc123def456..." `
    -GatewayUrl "http://gateway.example.com:8083/api" `
    -Body '{"input":"test"}'
```

#### Example 5: Auto-Detect Test Agent

```powershell
.\invoke-test-agent.ps1 `
    -RunTestAgent `
    -AgentKey "agk_live_abc123def456..." `
    -Method POST `
    -Body '{"input":"test data"}'
```

This will automatically:
- Find the Test Agent by searching for agents with "Test Agent" in the name
- Get the agent's endpoints
- Use the first active endpoint
- Query the database to find existing agent keys (shows prefix only)
- Prompt you to provide the full agent key

#### Example 6: Auto-Create Agent Key

If no agent key exists, automatically create one using a Cognito token:

```powershell
# First, get a Cognito token by logging in
$loginResponse = Invoke-RestMethod -Uri "http://localhost:8082/api/v1/security/auth/login" -Method Post -Body (@{
    email = "your-email@example.com"
    password = "your-password"
} | ConvertTo-Json) -ContentType "application/json"
$cognitoToken = $loginResponse.accessToken

# Then use it to auto-create an agent key
.\invoke-test-agent.ps1 `
    -RunTestAgent `
    -CognitoToken $cognitoToken `
    -AutoCreateKey `
    -Method POST `
    -Body '{"input":"test data"}'
```

This will:
- Find the Test Agent automatically
- Check if an agent key exists
- If no key exists, create one automatically using the Cognito token
- Use the newly created key to invoke the endpoint

#### Example 7: Regenerate Existing Agent Key

If an agent key exists, regenerate it (revokes old, creates new):

```powershell
.\invoke-test-agent.ps1 `
    -RunTestAgent `
    -CognitoToken $cognitoToken `
    -RegenerateKey `
    -Method POST `
    -Body '{"input":"test data"}'
```

This will:
- Find the Test Agent automatically
- Find existing agent keys in the database
- Regenerate the first active key (revokes old, creates new)
- Use the newly regenerated key to invoke the endpoint

### Additional Parameters

- **CognitoToken** (optional): Cognito JWT token for authenticating API calls to create/regenerate agent keys
- **AutoCreateKey** (optional): If specified and no agent key is found, automatically create one using the CognitoToken. Requires `-CognitoToken`
- **RegenerateKey** (optional): If specified and an agent key exists, regenerate it (revokes old, creates new). Requires `-CognitoToken`
- **DbConnectionString** (optional): PostgreSQL connection string for querying agent keys
- **DbHost**, **DbPort**, **DbName**, **DbUsername**, **DbPassword** (optional): Database connection parameters. Defaults: localhost, 5432, agent_hub_db, postgres, postgres
- **DbContainerName** (optional): Docker container name for PostgreSQL. Auto-detects if not specified

### How It Works

1. The script calls the Gateway Service endpoint: `/api/agent/{agentId}/endpoint/{endpointId}/invoke` (or `/invoke/async` for async)
2. The Gateway Service validates the agent key and retrieves agent/endpoint details
3. The Gateway Service generates HMAC signatures and forwards the request to the actual agent
4. For async requests, the script polls the status endpoint until completion
5. Results are displayed in the console

### Notes

- The agent key can be provided with or without the "Bearer " prefix
- For async requests, the script automatically polls for status and displays progress
- The script handles JSON validation and error responses
- All requests are authenticated using the provided agent key
- The Gateway Service handles HMAC signing automatically - you don't need to sign requests yourself

### Error Handling

The script will display detailed error information if:
- The agent key is invalid
- The agent or endpoint doesn't exist
- The request format is invalid
- The agent returns an error

Error responses include status codes, error messages, and validation errors when available.

