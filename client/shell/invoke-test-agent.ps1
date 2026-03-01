<#
.SYNOPSIS
    Invokes a Test Agent endpoint via the Gateway Service.
.DESCRIPTION
    This script calls the Gateway Service to invoke a Test Agent endpoint.
    It supports both synchronous and asynchronous invocations.
    The script handles HMAC signing and authentication automatically via the Gateway Service.
.PARAMETER AgentId
    The ID of the agent to invoke (required).
.PARAMETER EndpointId
    The ID of the endpoint to invoke (required).
.PARAMETER AgentKey
    The agent key for authentication (required). Format: Bearer token.
.PARAMETER Method
    HTTP method to use (GET, POST, PUT, DELETE). Default: POST.
.PARAMETER Body
    JSON body for the request. Can be a JSON string or path to a JSON file.
.PARAMETER QueryParams
    Query parameters as a hashtable. Example: @{param1='value1'; param2='value2'}
.PARAMETER Async
    If specified, invokes the endpoint asynchronously.
.PARAMETER GatewayUrl
    Base URL of the Gateway Service. Default: http://localhost:8083/api
.PARAMETER PollStatus
    For async requests, poll for status until completion. Default: $true
.PARAMETER PollInterval
    Interval in seconds between status polls for async requests. Default: 2
.PARAMETER Help
    Display this help message and exit.
.PARAMETER RunTestAgent
    Automatically find and use the Test Agent. Queries the core service to find the agent by name "Test Agent", gets its endpoints, and uses the first active endpoint.
.PARAMETER CoreServiceUrl
    Base URL of the Core Service for querying agent information. Default: http://localhost:8081/api/v1
.EXAMPLE
    .\invoke-test-agent.ps1 -AgentId "123" -EndpointId "456" -AgentKey "agk_live_abc123..." -Method POST -Body '{"message":"Hello"}'
.EXAMPLE
    .\invoke-test-agent.ps1 -AgentId "123" -EndpointId "456" -AgentKey "agk_live_abc123..." -Method GET -QueryParams @{param1='value1'}
.EXAMPLE
    .\invoke-test-agent.ps1 -AgentId "123" -EndpointId "456" -AgentKey "agk_live_abc123..." -Method POST -Body '{"message":"Hello"}' -Async
.EXAMPLE
    .\invoke-test-agent.ps1 -AgentId "123" -EndpointId "456" -AgentKey "agk_live_abc123..." -Method POST -Body "C:\path\to\body.json"
.EXAMPLE
    .\invoke-test-agent.ps1 -Help
    Display help information for this script.
.EXAMPLE
    .\invoke-test-agent.ps1 -RunTestAgent -AgentKey "agk_live_abc123..." -Method POST -Body '{"message":"Hello"}'
    Automatically find Test Agent and invoke its first endpoint.
.EXAMPLE
    .\invoke-test-agent.ps1 -RunTestAgent -AgentKey "agk_live_abc123..." -Method POST -Body '{"message":"Hello"}'
    Automatically find Test Agent and invoke its first endpoint.
#>

param(
    [Parameter(Mandatory=$false)]
    [switch]$Help,
    
    [Parameter(Mandatory=$false)]
    [switch]$RunTestAgent,
    
    [Parameter(Mandatory=$false)]
    [string]$AgentId,
    
    [Parameter(Mandatory=$false)]
    [string]$EndpointId,
    
    [Parameter(Mandatory=$false)]
    [string]$AgentKey,
    
    [Parameter(Mandatory=$false)]
    [ValidateSet('GET', 'POST', 'PUT', 'DELETE')]
    [string]$Method = 'POST',
    
    [Parameter(Mandatory=$false)]
    [string]$Body,
    
    [Parameter(Mandatory=$false)]
    [hashtable]$QueryParams,
    
    [Parameter(Mandatory=$false)]
    [switch]$Async,
    
    [Parameter(Mandatory=$false)]
    [string]$GatewayUrl = 'http://localhost:8083/api',
    
    [Parameter(Mandatory=$false)]
    [string]$CoreServiceUrl = 'http://localhost:8081/api/v1',
    
    [Parameter(Mandatory=$false)]
    [bool]$PollStatus = $true,
    
    [Parameter(Mandatory=$false)]
    [int]$PollInterval = 2
)

# Display help if requested
if ($Help) {
    Get-Help $MyInvocation.MyCommand.Path -Full
    exit 0
}

# If RunTestAgent is specified, find the Test Agent
if ($RunTestAgent) {
    Write-Host "Finding Test Agent..." -ForegroundColor Cyan
    
    try {
        # Search for agents by name "Test Agent"
        $searchName = [System.Uri]::EscapeDataString("Test Agent")
        $searchUrl = "$CoreServiceUrl/agents/search?name=$searchName"
        $agents = Invoke-RestMethod -Uri $searchUrl -Method Get -ErrorAction Stop
        
        if (-not $agents -or $agents.Count -eq 0) {
            Write-Error "Test Agent not found. Please ensure the Test Agent is registered."
            exit 1
        }
        
        # Find the first active agent with "Test Agent" in the name (case-insensitive)
        $testAgent = $agents | Where-Object { 
            $_.active -eq $true -and $_.name -like "*Test Agent*" 
        } | Select-Object -First 1
        
        if (-not $testAgent) {
            Write-Error "No active Test Agent found."
            exit 1
        }
        
        $AgentId = $testAgent.id
        Write-Host "  Found Test Agent: $($testAgent.name) (ID: $AgentId)" -ForegroundColor Green
        
        # Get agent details with endpoints
        $agentUrl = "$CoreServiceUrl/agents/$AgentId"
        $agentDetails = Invoke-RestMethod -Uri $agentUrl -Method Get -ErrorAction Stop
        
        if (-not $agentDetails.endpoints -or $agentDetails.endpoints.Count -eq 0) {
            Write-Error "Test Agent has no endpoints configured."
            exit 1
        }
        
        # Find the first active endpoint matching the requested HTTP method, or use the specified one
        if (-not $EndpointId) {
            $activeEndpoints = $agentDetails.endpoints | Where-Object { $_.active -eq $true }
            if (-not $activeEndpoints -or $activeEndpoints.Count -eq 0) {
                Write-Error "Test Agent has no active endpoints."
                exit 1
            }
            
            # Try to find an endpoint that matches the requested HTTP method
            $matchingEndpoint = $activeEndpoints | Where-Object { $_.httpMethod -eq $Method } | Select-Object -First 1
            if ($matchingEndpoint) {
                $EndpointId = $matchingEndpoint.id
                Write-Host "  Using endpoint: $($matchingEndpoint.name) (ID: $EndpointId, Method: $($matchingEndpoint.httpMethod))" -ForegroundColor Green
            } else {
                # If no matching method found, use the first active endpoint and warn
                $EndpointId = $activeEndpoints[0].id
                Write-Host "  Using endpoint: $($activeEndpoints[0].name) (ID: $EndpointId, Method: $($activeEndpoints[0].httpMethod))" -ForegroundColor Yellow
                Write-Warning "No endpoint found matching HTTP method '$Method'. Using endpoint with method '$($activeEndpoints[0].httpMethod)'. This may cause a 405 error."
                Write-Host "  Available endpoints:" -ForegroundColor Gray
                foreach ($ep in $activeEndpoints) {
                    Write-Host "    - $($ep.name) (Method: $($ep.httpMethod), ID: $($ep.id))" -ForegroundColor Gray
                }
            }
        } else {
            # Verify the specified endpoint exists
            $endpoint = $agentDetails.endpoints | Where-Object { $_.id -eq $EndpointId }
            if (-not $endpoint) {
                Write-Error "Endpoint $EndpointId not found for Test Agent."
                exit 1
            }
            if (-not $endpoint.active) {
                Write-Warning "Endpoint $EndpointId is not active."
            }
            Write-Host "  Using specified endpoint: $($endpoint.name) (ID: $EndpointId)" -ForegroundColor Green
        }
        
        Write-Host ""
    } catch {
        Write-Error "Failed to find Test Agent: $_"
        Write-Host "Make sure the Core Service is running at $CoreServiceUrl" -ForegroundColor Yellow
        exit 1
    }
}

# Validate required parameters
if (-not $AgentId) {
    Write-Error "AgentId is required. Use -RunTestAgent to auto-detect Test Agent, or provide -AgentId. Use -Help to see usage information."
    exit 1
}

if (-not $EndpointId) {
    Write-Error "EndpointId is required. Use -RunTestAgent to auto-detect, or provide -EndpointId. Use -Help to see usage information."
    exit 1
}

if (-not $AgentKey) {
    Write-Error "AgentKey is required. Use -Help to see usage information."
    exit 1
}

# Remove 'Bearer ' prefix if present
if ($AgentKey -like 'Bearer *') {
    $AgentKey = $AgentKey.Substring(7)
}

# Build the endpoint URL
$endpointPath = if ($Async) {
    "/agent/$AgentId/endpoint/$EndpointId/invoke/async"
} else {
    "/agent/$AgentId/endpoint/$EndpointId/invoke"
}

$url = "$GatewayUrl$endpointPath"

# Add query parameters if provided
if ($QueryParams -and $QueryParams.Count -gt 0) {
    $queryPairs = @()
    foreach ($key in $QueryParams.Keys) {
        $value = $QueryParams[$key]
        $encodedKey = [System.Uri]::EscapeDataString($key)
        $encodedValue = [System.Uri]::EscapeDataString($value)
        $queryPairs += "$encodedKey=$encodedValue"
    }
    $queryString = $queryPairs -join '&'
    $url = "$url?$queryString"
}

# Prepare headers
$headers = @{
    'Authorization' = "Bearer $AgentKey"
    'Content-Type' = 'application/json'
    'Accept' = 'application/json'
}

# Prepare body
$bodyContent = $null
if ($Body) {
    # Check if Body is a file path
    if (Test-Path $Body) {
        $bodyContent = Get-Content -Path $Body -Raw
        Write-Host "Reading body from file: $Body" -ForegroundColor Gray
    } else {
        $bodyContent = $Body
    }
    
    # Validate JSON
    try {
        $null = $bodyContent | ConvertFrom-Json
    } catch {
        Write-Error "Invalid JSON in body: $_"
        exit 1
    }
}

# Prepare request parameters
$requestParams = @{
    Uri = $url
    Method = $Method
    Headers = $headers
    ErrorAction = 'Stop'
}

if ($bodyContent -and ($Method -eq 'POST' -or $Method -eq 'PUT')) {
    $requestParams.Body = $bodyContent
}

# Make the request
Write-Host "Invoking agent endpoint..." -ForegroundColor Cyan
Write-Host "  URL: $url" -ForegroundColor Gray
Write-Host "  Method: $Method" -ForegroundColor Gray
if ($bodyContent) {
    Write-Host "  Body: $bodyContent" -ForegroundColor Gray
}
if ($Async) {
    Write-Host "  Type: Async" -ForegroundColor Gray
} else {
    Write-Host "  Type: Sync" -ForegroundColor Gray
}
Write-Host ""

try {
    $response = Invoke-RestMethod @requestParams
    
    if ($Async) {
        Write-Host "Async request submitted successfully!" -ForegroundColor Green
        Write-Host ""
        
        # Parse response
        $requestId = $response.requestId
        $statusUrl = $response.statusUrl
        $resultUrl = $response.resultUrl
        
        Write-Host "Request ID: $requestId" -ForegroundColor Yellow
        Write-Host "Status URL: $statusUrl" -ForegroundColor Gray
        Write-Host "Result URL: $resultUrl" -ForegroundColor Gray
        Write-Host ""
        
        if ($PollStatus) {
            Write-Host "Polling for status..." -ForegroundColor Cyan
            Write-Host ""
            
            $statusHeaders = @{
                'Accept' = 'application/json'
            }
            
            $maxWaitTime = 300 # 5 minutes max
            $startTime = Get-Date
            $lastStatus = $null
            
            while ($true) {
                try {
                    $statusResponse = Invoke-RestMethod -Uri $statusUrl -Method Get -Headers $statusHeaders -ErrorAction Stop
                    
                    $currentStatus = $statusResponse.status
                    $percentage = $statusResponse.percentageComplete
                    $stage = $statusResponse.stage
                    $elapsed = $statusResponse.elapsedTime
                    
                    # Only print if status changed
                    if ($currentStatus -ne $lastStatus) {
                        $statusColor = switch ($currentStatus) {
                            'PENDING' { 'Yellow' }
                            'IN_PROGRESS' { 'Cyan' }
                            'COMPLETED' { 'Green' }
                            'FAILED' { 'Red' }
                            default { 'White' }
                        }
                        
                        Write-Host "[$currentStatus]" -ForegroundColor $statusColor -NoNewline
                        if ($percentage -ne $null) {
                            Write-Host " - $percentage% complete" -ForegroundColor Gray -NoNewline
                        }
                        if ($stage) {
                            Write-Host " - Stage: $stage" -ForegroundColor Gray -NoNewline
                        }
                        if ($elapsed) {
                            Write-Host " - Elapsed: ${elapsed}s" -ForegroundColor Gray -NoNewline
                        }
                        Write-Host ""
                        
                        $lastStatus = $currentStatus
                    }
                    
                    if ($currentStatus -eq 'COMPLETED' -or $currentStatus -eq 'FAILED') {
                        Write-Host ""
                        Write-Host "Job finished with status: $currentStatus" -ForegroundColor $(if ($currentStatus -eq 'COMPLETED') { 'Green' } else { 'Red' })
                        Write-Host ""
                        
                        # Get final result
                        Write-Host "Fetching result..." -ForegroundColor Cyan
                        $resultResponse = Invoke-RestMethod -Uri $resultUrl -Method Get -Headers $statusHeaders -ErrorAction Stop
                        
                        Write-Host ""
                        Write-Host "=== RESULT ===" -ForegroundColor Yellow
                        if ($resultResponse.result) {
                            $resultJson = $resultResponse.result | ConvertTo-Json -Depth 10
                            Write-Host $resultJson -ForegroundColor White
                        } else {
                            Write-Host "No result data" -ForegroundColor Gray
                        }
                        
                        if ($resultResponse.error) {
                            Write-Host ""
                            Write-Host "=== ERROR ===" -ForegroundColor Red
                            Write-Host $resultResponse.error -ForegroundColor Red
                        }
                        
                        break
                    }
                    
                    # Check timeout
                    $elapsedTime = (Get-Date) - $startTime
                    if ($elapsedTime.TotalSeconds -gt $maxWaitTime) {
                        Write-Host ""
                        Write-Warning "Timeout waiting for job completion (max $maxWaitTime seconds)"
                        Write-Host "You can check the status manually using:"
                        Write-Host "  Invoke-RestMethod -Uri '$statusUrl' -Method Get" -ForegroundColor Gray
                        break
                    }
                    
                    Start-Sleep -Seconds $PollInterval
                } catch {
                    Write-Warning "Error polling status: $_"
                    Start-Sleep -Seconds $PollInterval
                }
            }
        } else {
            Write-Host "Status polling disabled. Use the URLs above to check status manually." -ForegroundColor Yellow
        }
    } else {
        Write-Host "=== RESPONSE ===" -ForegroundColor Green
        Write-Host ""
        $responseJson = $response | ConvertTo-Json -Depth 10
        Write-Host $responseJson -ForegroundColor White
    }
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    $statusDescription = $_.Exception.Response.StatusDescription
    
    Write-Host ""
    Write-Host "=== ERROR ===" -ForegroundColor Red
    Write-Host "Status: $statusCode $statusDescription" -ForegroundColor Red
    
    # Try to get error details from response
    try {
        $errorStream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($errorStream)
        $errorBody = $reader.ReadToEnd()
        $errorJson = $errorBody | ConvertFrom-Json
        
        if ($errorJson.message) {
            Write-Host "Message: $($errorJson.message)" -ForegroundColor Red
        }
        if ($errorJson.error) {
            Write-Host "Error: $($errorJson.error)" -ForegroundColor Red
        }
        if ($errorJson.errors) {
            Write-Host "Errors:" -ForegroundColor Red
            $errorJson.errors | ConvertTo-Json | Write-Host -ForegroundColor Red
        }
    } catch {
        Write-Host "Error details: $_" -ForegroundColor Red
    }
    
    exit 1
}

