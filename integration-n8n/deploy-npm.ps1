# Install published n8n-nodes-tollara from npm into local n8n Docker (final pre-submit test).
#
# Usage (from integration-n8n):
#   .\deploy-npm.ps1
#   .\deploy-npm.ps1 -Version 0.0.10
#   .\deploy-npm.ps1 -SkipPull

param(
    [string]$Version = '',
    [switch]$SkipPull
)

$ErrorActionPreference = 'Stop'

$integrationRoot = $PSScriptRoot
$dockerRoot = Join-Path $integrationRoot 'docker'
$containerName = 'tollara-n8n-dev'
$composeFile = Join-Path $dockerRoot 'docker-compose.npm.yml'

if (-not $Version) {
    $Version = (Get-Content (Join-Path $integrationRoot 'package.json') | ConvertFrom-Json).version
}

function Wait-ContainerRunning {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [int]$TimeoutSeconds = 90
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

Write-Host "Tollara n8n - install from npm"
Write-Host "  package: n8n-nodes-tollara@$Version"
Write-Host "  compose: $composeFile"

Push-Location $dockerRoot
try {
    if (-not $SkipPull) {
        Write-Host ""
        Write-Host '==> Pulling latest n8n image'
        docker compose -f $composeFile pull
        if ($LASTEXITCODE -ne 0) {
            throw "docker compose pull failed with exit code $LASTEXITCODE"
        }
    }

    Write-Host ""
    Write-Host '==> Starting n8n (npm mode, no bind-mount)'
    docker compose -f $composeFile up -d --force-recreate
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose up failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

Wait-ContainerRunning -Name $containerName

Write-Host ""
Write-Host "==> Installing n8n-nodes-tollara@$Version from registry.npmjs.org"
docker exec $containerName sh -c "cd /home/node/.n8n/nodes && npm install n8n-nodes-tollara@$Version --no-fund --no-audit"
if ($LASTEXITCODE -ne 0) {
    throw 'npm install in container failed'
}

$nodesPackageJson = @"
{
  `"name`": `"installed-nodes`",
  `"private`": true,
  `"dependencies`": {
    `"n8n-nodes-tollara`": `"$Version`"
  }
}
"@
Write-Host ""
Write-Host '==> Updating installed-nodes package.json in container'
$nodesPackageJson | docker exec -i $containerName tee /home/node/.n8n/nodes/package.json | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw 'Failed to update /home/node/.n8n/nodes/package.json'
}

Write-Host ""
Write-Host '==> Building host package (for community registry sync metadata)'
Push-Location $integrationRoot
try {
    & npm run build --silent
    if ($LASTEXITCODE -ne 0) {
        throw 'npm run build failed'
    }
}
finally {
    Pop-Location
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
Write-Host '==> Verifying npm package loads in container'
docker exec $containerName node -e "const p=require('/home/node/.n8n/nodes/node_modules/n8n-nodes-tollara/package.json'); const fs=require('fs'); const ok=fs.existsSync('/home/node/.n8n/nodes/node_modules/n8n-nodes-tollara/dist/nodes/TollaraInvoke/TollaraInvoke.node.js'); if(!ok) process.exit(1); console.log('OK', p.name, p.version, p.n8n.nodes.length, 'nodes');"
if ($LASTEXITCODE -ne 0) {
    throw 'Published package verification failed'
}

Write-Host ""
Write-Host '==> Restarting n8n'
docker restart $containerName | Out-Null
Wait-ContainerRunning -Name $containerName
Start-Sleep -Seconds 5

$examplePath = '/home/node/.n8n/nodes/node_modules/n8n-nodes-tollara/example-workflows'
Write-Host ""
Write-Host 'Deploy complete (npm package).'
Write-Host "  n8n UI:      http://localhost:5678"
Write-Host "  package:     n8n-nodes-tollara@$Version (from npm)"
Write-Host "  community:   http://localhost:5678/settings/community-nodes"
Write-Host "  workflows:   import from container path $examplePath"
Write-Host ""
Write-Host 'To switch back to live repo bind-mount dev:  .\deploy-local.ps1'
Write-Host ""
Write-Host 'After importing workflows: set YOUR_* placeholders and Service Secret on Tollara nodes.'
