#Requires -Version 5.1
<#
.SYNOPSIS
    Unified test runner - BE, Mock, FE, Electron, and optional E2E.
    Prints "ALL GREEN" + exits 0 on full pass; prints "FAILED" list + exits 1 otherwise.
    Created by Adikarthik Gupta C B
.PARAMETER SkipE2E
    When set, skips the E2E build + e2e suite.
#>
param(
    [switch]$SkipE2E
)

$ErrorActionPreference = 'Continue'

Write-Host '==== test.ps1 - unified test runner ===='

$root = Split-Path -Parent $PSScriptRoot
$failedSections = [System.Collections.Generic.List[string]]::new()

function Invoke-Section {
    param([string]$Name, [scriptblock]$Body)
    Write-Host ''
    Write-Host "---- $Name ----"
    $sectionFailed = $false
    $failReason = ''
    # Temporarily disable strict mode inside sections to avoid PS 5.1
    # false-positive 'Statement' errors from native-executable stderr streams.
    Set-StrictMode -Off
    try {
        & $Body
        $ec = $LASTEXITCODE
        if ($null -ne $ec -and [int]$ec -ne 0) {
            $sectionFailed = $true
            $failReason = "exit code $ec"
        }
    } catch {
        $sectionFailed = $true
        $failReason = $_.Exception.Message
    }
    Set-StrictMode -Version Latest
    if ($sectionFailed) {
        Write-Host "---- $Name FAILED: $failReason ----"
        $script:failedSections.Add($Name)
    } else {
        Write-Host "---- $Name PASSED ----"
    }
}

Set-StrictMode -Version Latest

# 1. BE verify
Invoke-Section -Name 'BE verify' -Body {
    $env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
    & mvn -f (Join-Path $root 'backend\pom.xml') verify
    if ($LASTEXITCODE -ne 0) { throw "mvn verify exited $LASTEXITCODE" }
}

# 2. Mock tests
Invoke-Section -Name 'Mock tests' -Body {
    $env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
    & mvn -f (Join-Path $root 'mock-naukri\pom.xml') test
    if ($LASTEXITCODE -ne 0) { throw "mvn test exited $LASTEXITCODE" }
}

# 3. FE tests
Invoke-Section -Name 'FE tests' -Body {
    $feDir = Join-Path $root 'frontend'
    & npm.cmd --prefix $feDir run test:ci
    if ($LASTEXITCODE -ne 0) { throw "npm test:ci exited $LASTEXITCODE" }
}

# 4. Electron tests
Invoke-Section -Name 'Electron tests' -Body {
    $electronDir = Join-Path $root 'electron'
    & npm.cmd --prefix $electronDir test
    if ($LASTEXITCODE -ne 0) { throw "npm test exited $LASTEXITCODE" }
}

# 5. E2E (optional)
if (-not $SkipE2E) {
    Invoke-Section -Name 'E2E build' -Body {
        & (Join-Path $PSScriptRoot 'build.ps1') -Variant E2E
        if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) { throw "build.ps1 E2E exited $LASTEXITCODE" }
    }

    Invoke-Section -Name 'E2E tests' -Body {
        $e2eDir = Join-Path $root 'e2e'
        # Install dependencies (use ci if lockfile exists, else install)
        if (Test-Path (Join-Path $e2eDir 'package-lock.json')) {
            & npm.cmd --prefix $e2eDir ci
        } else {
            & npm.cmd --prefix $e2eDir install
        }
        if ($LASTEXITCODE -ne 0) { throw "npm install (e2e) exited $LASTEXITCODE" }
        # Install Playwright Chromium browser binary
        & npx --prefix $e2eDir playwright install chromium
        if ($LASTEXITCODE -ne 0) { throw "playwright install chromium exited $LASTEXITCODE" }
        # Run the E2E suite
        & npm.cmd --prefix $e2eDir test
        if ($LASTEXITCODE -ne 0) { throw "npm test (e2e) exited $LASTEXITCODE" }
    }
}

Write-Host ''
if ($failedSections.Count -eq 0) {
    Write-Host 'ALL GREEN'
    exit 0
} else {
    Write-Host "FAILED sections: $($failedSections -join ', ')"
    exit 1
}

