# Build integration-n8n and deploy to local n8n Docker (one command).
#
# Usage (from integration-n8n):
#   .\deploy-local.ps1
#   .\deploy-local.ps1 -RunTests
#   .\deploy-local.ps1 -SkipPull
#
# Or:
#   npm run deploy:local

param(
    [switch]$RunTests,
    [switch]$SkipPull
)

$ErrorActionPreference = 'Stop'

$integrationRoot = $PSScriptRoot
$dockerRoot = Join-Path $integrationRoot 'docker'
$containerName = 'tollara-n8n-dev'

function Invoke-NpmStep {
    param(
        [Parameter(Mandatory = $true)][string]$Label,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    Write-Host ""
    Write-Host "==> $Label ($WorkingDirectory)"
    Push-Location $WorkingDirectory
    try {
        & npm @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "npm $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}

function Wait-ContainerRunning {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [int]$TimeoutSeconds = 60
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $status = docker inspect -f '{{.State.Running}}' $Name 2>$null
        if ($status -eq 'true') {
            return
        }
        Start-Sleep -Seconds 2
    }
    throw "Container '$Name' did not start within ${TimeoutSeconds}s"
}

Write-Host "Tollara n8n local deploy"
Write-Host "  integration-n8n: $integrationRoot"
Write-Host "  docker:          $dockerRoot"

Invoke-NpmStep -Label 'Installing n8n-nodes-tollara dependencies' -WorkingDirectory $integrationRoot -Arguments @('install', '--no-fund', '--no-audit')
Invoke-NpmStep -Label 'Building n8n-nodes-tollara' -WorkingDirectory $integrationRoot -Arguments @('run', 'build')

if ($RunTests) {
    Invoke-NpmStep -Label 'Running integration-n8n tests' -WorkingDirectory $integrationRoot -Arguments @('test')
}

Push-Location $dockerRoot
try {
    if (-not $SkipPull) {
        Write-Host ""
        Write-Host '==> Pulling latest n8n image'
        docker compose pull
        if ($LASTEXITCODE -ne 0) {
            throw "docker compose pull failed with exit code $LASTEXITCODE"
        }
    }

    Write-Host ""
    Write-Host '==> Starting n8n (recreate container)'
    docker compose up -d --force-recreate
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose up failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

Wait-ContainerRunning -Name $containerName

$version = (Get-Content (Join-Path $integrationRoot 'package.json') | ConvertFrom-Json).version
Write-Host ""
Write-Host "==> Updating installed-nodes package.json in container (v$version)"
$nodesPackageJson = @"
{
  `"name`": `"installed-nodes`",
  `"private`": true,
  `"dependencies`": {
    `"n8n-nodes-tollara`": `"$version`"
  }
}
"@
$nodesPackageJson | docker exec -i $containerName tee /home/node/.n8n/nodes/package.json | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Failed to update /home/node/.n8n/nodes/package.json in container"
}

Write-Host ""
Write-Host '==> Syncing community node registry (SQLite)'
& powershell -ExecutionPolicy Bypass -File (Join-Path $dockerRoot 'sync-community-db.ps1') -SkipBuild
if ($LASTEXITCODE -ne 0) {
    throw 'Community registry sync failed'
}

Write-Host ""
Write-Host '==> Stripping stale tollaraApi credential refs from saved workflows'
& powershell -ExecutionPolicy Bypass -File (Join-Path $dockerRoot 'strip-stale-credentials.ps1')
if ($LASTEXITCODE -ne 0) {
    throw 'Stale credential cleanup failed'
}

Write-Host ""
Write-Host '==> Verifying community package loads in container'
docker exec $containerName node /home/node/.n8n/nodes/node_modules/n8n-nodes-tollara/scripts/verify-package-load.mjs
if ($LASTEXITCODE -ne 0) {
    throw 'Community package verification failed — check index.js, dist/, and bundled tollaraSdk.js'
}

Write-Host ""
Write-Host '==> Restarting n8n (reload node registry in running process)'
docker restart $containerName | Out-Null
Wait-ContainerRunning -Name $containerName
Start-Sleep -Seconds 5

Write-Host ""
Write-Host 'Deploy complete.'
Write-Host "  n8n UI:     http://localhost:5678"
Write-Host "  package:    n8n-nodes-tollara@$version"
Write-Host "  community:  http://localhost:5678/settings/community-nodes"
Write-Host ""
Write-Host 'IMPORTANT after importing example workflows:'
Write-Host '  1. Run: npm run repair:workflows   (fixes Tollara Verify Request empty params — n8n import bug)'
Write-Host '  2. Close and reopen the workflow tab; hard-refresh browser (Ctrl+F5) if icons still look wrong'
Write-Host '  3. Set YOUR_SERVICE_SECRET on each Tollara node; enable Set API Endpoints on nodes for local API URLs'
Write-Host '  4. Delete duplicate workflows with the same name before re-importing (do not import over the top)'
Write-Host ""
Write-Host 'You do NOT need to delete the Docker image or n8n_data volume.'
