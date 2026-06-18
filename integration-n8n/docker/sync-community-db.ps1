# Sync n8n SQLite community-node registry with local package.json (fixes stale UI metadata).
param(
    [switch]$SkipBuild
)

Set-Location $PSScriptRoot
$integrationRoot = Join-Path $PSScriptRoot ".."

if (-not $SkipBuild) {
    Push-Location $integrationRoot
    npm run build
    if ($LASTEXITCODE -ne 0) { Pop-Location; exit $LASTEXITCODE }
    Pop-Location
}

$sql = node (Join-Path $integrationRoot "scripts\sync-community-registry.mjs")
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Stopping n8n to sync community node registry..."
docker compose stop

$volumeName = "docker_n8n_data"
$sql | docker run --rm -i -v "${volumeName}:/data" --user 1000:1000 keinos/sqlite3 sqlite3 /data/database.sqlite

if ($LASTEXITCODE -ne 0) {
    Write-Host "Failed to sync community registry (exit $LASTEXITCODE)"
    docker compose start
    exit $LASTEXITCODE
}

Write-Host "Starting n8n..."
docker compose start
Start-Sleep -Seconds 8

$version = (Get-Content (Join-Path $integrationRoot "package.json") | ConvertFrom-Json).version
Write-Host "Community registry synced to n8n-nodes-tollara@$version"
Write-Host "Refresh http://localhost:5678/settings/community-nodes"
