# Build local n8n-nodes-tollara and start n8n with a bind mount (no npm publish required).
Set-Location $PSScriptRoot
$integrationRoot = Join-Path $PSScriptRoot ".."

Write-Host "Building n8n-nodes-tollara from $integrationRoot ..."
Push-Location $integrationRoot
npm install --no-fund --no-audit
if ($LASTEXITCODE -ne 0) { Pop-Location; exit $LASTEXITCODE }
npm run build
if ($LASTEXITCODE -ne 0) { Pop-Location; exit $LASTEXITCODE }
Pop-Location

docker compose pull
docker compose up -d --force-recreate

Start-Sleep -Seconds 5
$version = (Get-Content (Join-Path $integrationRoot "package.json") | ConvertFrom-Json).version
$nodesPackageJson = @"
{
  `"name`": `"installed-nodes`",
  `"private`": true,
  `"dependencies`": {
    `"n8n-nodes-tollara`": `"$version`"
  }
}
"@
$nodesPackageJson | docker exec -i tollara-n8n-dev tee /home/node/.n8n/nodes/package.json | Out-Null

Write-Host "Syncing community node registry in SQLite (UI reads this)..."
powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "sync-community-db.ps1") -SkipBuild
Write-Host ""
Write-Host "n8n: http://localhost:5678"
Write-Host "Tollara nodes loaded from local folder (package.json version: $((Get-Content (Join-Path $integrationRoot 'package.json') | ConvertFrom-Json).version))"
Write-Host ""
Write-Host "After code changes: npm run build in integration-n8n, then: .\sync-community-db.ps1"
Write-Host "Logs: docker compose -f $PSScriptRoot\docker-compose.yml logs -f"
Write-Host "Stop:  docker compose -f $PSScriptRoot\docker-compose.yml down"
