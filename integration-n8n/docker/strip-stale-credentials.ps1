# Remove stale credentials.tollaraApi from saved workflows (fixes "Unnamed credential" UI).
param(
    [string]$VolumeName = 'docker_n8n_data'
)

$ErrorActionPreference = 'Stop'
$integrationRoot = Join-Path $PSScriptRoot '..'
$scriptsDir = Join-Path $integrationRoot 'scripts'

Write-Host 'Stopping n8n before workflow credential cleanup...'
Push-Location $PSScriptRoot
try {
    docker compose stop
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose stop failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

Write-Host 'Stripping stale tollaraApi credential refs and repairing Tollara node parameters...'
docker run --rm `
    -v "${VolumeName}:/n8n" `
    -v "${scriptsDir}:/scripts:ro" `
    node:22-alpine `
    sh -c "apk add --no-cache sqlite > /dev/null && node /scripts/repair-tollara-workflows.mjs /n8n/database.sqlite"

if ($LASTEXITCODE -ne 0) {
    throw 'repair-tollara-workflows failed'
}

Write-Host 'Starting n8n...'
Push-Location $PSScriptRoot
try {
    docker compose start
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose start failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

Start-Sleep -Seconds 5
Write-Host 'Stale tollaraApi credential refs removed from workflows.'
