# Start local n8n with n8n-nodes-tollara pre-installed.
Set-Location $PSScriptRoot
docker compose pull
docker compose up -d
Write-Host ""
Write-Host "n8n starting at http://localhost:5678"
Write-Host "First visit: create your owner account, then search 'Tollara' in the node panel."
Write-Host ""
Write-Host "Logs: docker compose -f $PSScriptRoot\docker-compose.yml logs -f"
Write-Host "Stop:  docker compose -f $PSScriptRoot\docker-compose.yml down"
