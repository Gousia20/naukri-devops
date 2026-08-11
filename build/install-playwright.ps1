#Requires -Version 5.1
<#
.SYNOPSIS
    Downloads Chromium for Playwright into electron/resources/playwright/.
    Idempotent - no-op if chrome.exe already present.
    Created by Adikarthik Gupta C B
#>
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

Write-Host '==== install-playwright ===='

$env:JAVA_HOME ='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
$root           = Split-Path -Parent $PSScriptRoot
$playwrightDir  = Join-Path $root 'electron\resources\playwright'

# Idempotency check
$chromePaths = Get-ChildItem -Path $playwrightDir -Recurse -Filter 'chrome.exe' -ErrorAction SilentlyContinue
if ($chromePaths) {
    Write-Host "Chromium already present at $playwrightDir - skipping install."
    Write-Host '==== install-playwright DONE (cached) ===='
    exit 0
}

if (-not (Test-Path $playwrightDir)) {
    New-Item -ItemType Directory -Force -Path $playwrightDir | Out-Null
}

# Set PLAYWRIGHT_BROWSERS_PATH so the CLI downloads into our managed folder
$env:PLAYWRIGHT_BROWSERS_PATH = $playwrightDir
Write-Host "PLAYWRIGHT_BROWSERS_PATH : $($env:PLAYWRIGHT_BROWSERS_PATH)"

$pomPath = Join-Path $root 'backend\pom.xml'
Write-Host "Running   : mvn exec:java (Playwright CLI install chromium)"
# Use array so PowerShell doesn't parse -Dexec.* as named parameters
$mvnArgs = @(
    '-f', $pomPath,
    'exec:java',
    '-Dexec.mainClass=com.microsoft.playwright.CLI',
    '-Dexec.args=install chromium --with-deps'
)
& mvn @mvnArgs
if ($LASTEXITCODE -ne 0) { throw "install-playwright: mvn exec:java exited with code $LASTEXITCODE" }

# Verify
$chromePaths = Get-ChildItem -Path $playwrightDir -Recurse -Filter 'chrome.exe' -ErrorAction SilentlyContinue
if (-not $chromePaths) {
    throw "install-playwright: chrome.exe not found under $playwrightDir after install"
}
Write-Host "Chromium installed at $playwrightDir"
Write-Host '==== install-playwright DONE ===='
