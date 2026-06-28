# Backward-compatible entry point — use ..\deploy-local.ps1 from integration-n8n instead.
param(
    [switch]$RunTests,
    [switch]$SkipPull
)

& (Join-Path (Join-Path $PSScriptRoot '..') 'deploy-local.ps1') @PSBoundParameters
exit $LASTEXITCODE
