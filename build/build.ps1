#Requires -Version 5.1
<#
.SYNOPSIS
    Orchestrator - runs all six build phases in order.
    Created by Adikarthik Gupta C B
.PARAMETER Variant
    Ship (default) - produces NSIS + portable installers.
    E2E  - also stages mock-naukri.jar into electron/resources/mock/.
#>
param(
    [ValidateSet('Ship', 'E2E')]
    [string]$Variant = 'Ship'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

Write-Host "==== NaukriAutomator Build (Variant=$Variant) ===="

$phasesDir = Join-Path $PSScriptRoot 'phases'

function Invoke-Phase {
    param([string]$Name, [string]$Script, [hashtable]$Params)
    Write-Host ''
    Write-Host ">>> Phase: $Name"
    if ($Params -and $Params.Count -gt 0) {
        & $Script @Params
    } else {
        & $Script
    }
    if ($null -ne $LASTEXITCODE -and $LASTEXITCODE -ne 0) { throw "Phase '$Name' failed with exit code $LASTEXITCODE" }
    Write-Host ">>> Phase: $Name - OK"
}

# Phase 1 - fetch JRE (idempotent)
Invoke-Phase -Name 'fetch-jre'      -Script (Join-Path $PSScriptRoot 'fetch-jre.ps1')           -Params @{}

# Phase 2 - install Playwright (idempotent)
Invoke-Phase -Name 'playwright'     -Script (Join-Path $PSScriptRoot 'install-playwright.ps1')  -Params @{}

# Phase 3 - build backend
Invoke-Phase -Name 'build-backend'  -Script (Join-Path $phasesDir 'build-backend.ps1')          -Params @{}

# Phase 4 - build mock
Invoke-Phase -Name 'build-mock'     -Script (Join-Path $phasesDir 'build-mock.ps1')             -Params @{}

# Phase 5 - build frontend
Invoke-Phase -Name 'build-frontend' -Script (Join-Path $phasesDir 'build-frontend.ps1')         -Params @{}

# Phase 6 - build electron (passes Variant)
Invoke-Phase -Name 'build-electron' -Script (Join-Path $phasesDir 'build-electron.ps1')         -Params @{ Variant = $Variant }

Write-Host ''
Write-Host "==== NaukriAutomator Build COMPLETE (Variant=$Variant) ===="
