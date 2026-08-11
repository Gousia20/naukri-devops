#Requires -Version 5.1
<#
.SYNOPSIS
    Phase: build-backend - compiles and packages the Spring Boot backend JAR.
    Created by Adikarthik Gupta C B
#>
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

Write-Host '==== build-backend phase ===='

$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
# Resolve root: this script lives at <root>\build\phases\build-backend.ps1
$root    = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$pomPath = Join-Path $root 'backend\pom.xml'

Write-Host "JAVA_HOME : $($env:JAVA_HOME)"
Write-Host "POM       : $pomPath"
Write-Host "Running   : mvn clean package -DskipTests -Dmaven.test.skip=true"

# Use array to avoid PowerShell interpreting -D as a switch
$mvnArgs = @('-f', $pomPath, 'clean', 'package', '-DskipTests', '-Dmaven.test.skip=true')
& mvn @mvnArgs
if ($LASTEXITCODE -ne 0) { throw "build-backend: mvn exited with code $LASTEXITCODE" }

Write-Host '==== build-backend DONE ===='

