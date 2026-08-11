#Requires -Version 5.1
<#
.SYNOPSIS
    Backend-only "hotpatch" build for fast iteration on an already-installed
    NaukriAutomator app. Compiles the Spring Boot fat JAR and drops it at
    <root>\dist\hotpatch\naukri-be.jar so you have one stable path to copy
    from every time.

    Typical workflow:
        1. Install NaukriAutomator once on the target machine (NSIS installer).
        2. Iterate on backend code here.
        3. .\build\phases\build-backend-only.ps1
        4. Copy dist\hotpatch\naukri-be.jar to
           %LOCALAPPDATA%\Programs\NaukriAutomator\resources\backend\naukri-be.jar
           on the target machine, overwriting the existing jar.
        5. Relaunch the app.

    Only for pure-Java changes (automation, selectors, orchestrator, REST,
    retry, timeouts). Frontend / Electron / preload changes still need the
    full pipeline (build\build.ps1).

    Created by Adikarthik Gupta C B
#>
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

Write-Host '==== build-backend-only (hotpatch) ===='

$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
# Resolve root: this script lives at <root>\build\phases\build-backend-only.ps1
$root       = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$pomPath    = Join-Path $root 'backend\pom.xml'
$builtJar   = Join-Path $root 'backend\target\naukri-be.jar'
$hotpatchDir = Join-Path $root 'dist\hotpatch'
$dropPath   = Join-Path $hotpatchDir 'naukri-be.jar'

Write-Host "JAVA_HOME : $($env:JAVA_HOME)"
Write-Host "POM       : $pomPath"
Write-Host "Drop path : $dropPath"
Write-Host "Running   : mvn clean package -DskipTests -Dmaven.test.skip=true"

$mvnArgs = @('-f', $pomPath, 'clean', 'package', '-DskipTests', '-Dmaven.test.skip=true')
& mvn @mvnArgs
if ($LASTEXITCODE -ne 0) { throw "build-backend-only: mvn exited with code $LASTEXITCODE" }

if (-not (Test-Path $builtJar)) {
    throw "build-backend-only: expected jar not found at $builtJar"
}

if (-not (Test-Path $hotpatchDir)) {
    New-Item -ItemType Directory -Path $hotpatchDir -Force | Out-Null
}

Copy-Item -Path $builtJar -Destination $dropPath -Force
$sizeMb = [math]::Round((Get-Item $dropPath).Length / 1MB, 1)

Write-Host ''
Write-Host "==== build-backend-only DONE ($sizeMb MB) ===="
Write-Host "Drop     : $dropPath"
Write-Host "Copy to  : %LOCALAPPDATA%\Programs\NaukriAutomator\resources\backend\naukri-be.jar"

